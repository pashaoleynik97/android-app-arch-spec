# Auth session — tokens, refresh, keep-alive, sign-in (component: auth-session)

Recommended guidance (SHOULD) from `.arch-spec/spec/specs/recommended/auth-session.md` (`AUTH-*`), plus ADR-0008
(separate clients) and ADR-0009 (activity-gated keep-alive). Modules: `:auth` (`{{ROOT_PACKAGE}}.auth`, no UI)
and the sign-in trio `:login-ui-api` / `:login-ui` / `:login`. Host side: the `session` package and
`SessionHostModule` of `:app`.

Contents: 1. Surface · 2. Storage · 3. Refresh · 4. Keep-alive · 5. Sign-in and sign-out ·
6. Host integration · 7. Testing · 8. Changing it

---

## 1. Surface

| Type | Role |
|---|---|
| `AuthManager` | the only way into the session: `authState`, `resolveSession()`, `login()`, `logout()`, `endSession(reason)`, `revokeRemoteSession()`, lock-free `current…()` reads |
| `AuthState` | `Initializing` · `Authenticated(userId, accessToken)` · `Unauthenticated(reason, sessionEndReason?)` |
| `LoginOutcome` | `Success` · `InvalidCredentials` · `AccountDisabled` · `Rejected(code)` · `Unavailable(cause)` |
| `SessionEndReason` | the backend's precise reason (`details.reason` of a refused refresh); unknown → `null` |
| `SessionShell` | host view of the session: `destination` + `notice`; no UI, JVM-tested |
| `UserActivityTracker` | last user interaction (monotonic); stamped by the host |

Internal: `DefaultAuthManager` (also the `RefreshCoordinator`), `RetrofitAuthService` (the classifier),
`AuthInterceptor`, `TokenAuthenticator`, the stores. Features talk to their own interactor, never to these
(DATA-02); a feature that needs the user's name gets it through its interactor (DATA-05).

**AUTH-01** — One source of truth: `authState`, `Authenticated` re-emitted on every rotation, lock-free reads.
*Why:* host navigation, sockets and per-user caches all derive from one flow; the bearer interceptor reads on
every request and must never wait for a refresh.
<!-- origin: arch-spec AUTH-01 -->

## 2. Storage

**AUTH-02** — Only the refresh token is persisted — AES-GCM under an Android Keystore key
(`KeystoreSecureTokenStore`); the access token stays in memory; a decrypt failure reads as "no session". No
EncryptedSharedPreferences. *Why:* the key never leaves the device, and a lost key must mean "sign in again",
never a crash loop (KERNEL-17).
<!-- origin: arch-spec AUTH-02 -->

**AUTH-03** — The token and profile files (`KeystoreSecureTokenStore.PREFS_FILE_NAME`, `SharedPrefsProfileStore.PREFS_FILE_NAME`) are excluded from
Auto Backup and device transfer, inside the marker blocks of `app/src/main/res/xml/backup_rules.xml` and
`app/src/main/res/xml/data_extraction_rules.xml`. Rename a store's file → update both. *Why:* a restored
Keystore-encrypted token can never be decrypted; a restored profile shows a stranger's name.
<!-- origin: arch-spec AUTH-03 -->

**AUTH-04** — A transient failure during the cold-start check keeps the state `Initializing`, keeps the stored
token, and answers `SessionResolution.CONNECTION_ERROR`. *Why:* flaky Wi-Fi at launch must not sign anyone out.
<!-- origin: arch-spec AUTH-04 -->

## 3. Refresh

**AUTH-05** — Refresh is single-flight: one mutex, one shared `Deferred` started on the `@AuthScope` app scope,
an in-lock double-check ("already rotated past the caller's token? hand out the current one"), and the in-flight
slot cleared in `finally` under NonCancellable. *Why:* refresh tokens are single-use, so two refreshes make the
server refuse one (a false logout); a cancelled waiter must not cancel the shared refresh, nor leave a completed
`AuthFailed` parked in the slot forever.
<!-- origin: arch-spec AUTH-05 -->

**AUTH-06** — Every identity change bumps the session generation; a refresh re-checks it before the network call
and before writing, answering `Stale` on a mismatch. *Why:* a late answer must not resurrect a signed-out session
or overwrite another user's, and must not spend the newer session's single-use token.
<!-- origin: arch-spec AUTH-06 -->

**AUTH-07** — Only a definitive refusal ends a session (401 on refresh; a known login refusal code). 5xx, IO,
timeouts, unknown 4xx, an empty 2xx or a blank token are `Transient`. *Why:* DATA-17 — never a false logout.
<!-- origin: arch-spec AUTH-07 -->

**AUTH-08** — `TokenAuthenticator` (on the business client via its `@ApiClient` authenticator seam) routes each
401 into the coordinator with `runBlocking`, retries once, expires the session on a second 401 for the caller's
token only, and ignores requests tagged `RefreshPolicy` NeverRefresh. *Why:* OkHttp calls authenticators
concurrently without single-flight; one retry stops loops; background work must not extend a session.
<!-- origin: arch-spec AUTH-08 -->

**AUTH-09** — Login, refresh and logout use `@AuthClient` / `@AuthRetrofit`: no bearer, no authenticator, same
`NetworkConfig` and `Json`. *Why:* a 401 on refresh must never trigger another refresh.
<!-- origin: arch-spec AUTH-09 -->

Lock discipline in `DefaultAuthManager`: identity writes only under the mutex; `wipeLocked()` clears memory, then
the store, and cancels the keep-alive **last** (it may be running inside that job). Network calls never run while
holding the lock, except the refresh's own commit section.

## 4. Keep-alive

**AUTH-10** — Shortly before expiry (`ProactiveRefreshConfig.leadMillis`, at most half the lifetime) the loop
refreshes only if the user interacted since the last commit; otherwise it suspends on
`UserActivityTracker.interactions` — it never returns and never ends the session. *Why:* used sessions stay
alive, abandoned ones lapse on the server's idle window (ADR-0009), and the first touch after an idle spell revives
the loop.
<!-- origin: arch-spec AUTH-10 -->

**AUTH-11** — Activity is stamped by the host: `SessionHostExtension` observes every pointer-down in the window
(initial pass, never consumed). Key-driven apps also call `recordInteraction()` from their key handling. *Why:* a
per-screen stamp is forgotten on the next screen; observing without consuming cannot break a gesture.
<!-- origin: arch-spec AUTH-11 -->

## 5. Sign-in and sign-out

**AUTH-12** — Sign-out is local-first: `LoginInteractor.signOut()` = `revokeRemoteSession()` (best effort, three
attempts, any answer below 500 is done) then `logout()` — always. The profile cache survives. *Why:* signing out
offline must work; a failed revoke must not keep tokens on the device.
<!-- origin: arch-spec AUTH-12 -->

**AUTH-16** — The sign-in ViewModel holds the password privately (never in `LoginUxState`), validates on submit,
clears a field's error when it changes, keeps the input on `Unavailable`, clears the password on a refusal, stays
"submitting" on success and never navigates. *Why:* KERNEL-05, DATA-17, DATA-31; the session decides where the
user goes.
<!-- origin: arch-spec AUTH-16 -->

The trio is the reference shape for a feature over the session: `LoginInteractor` maps `LoginOutcome` to the
feature's `SignInOutcome`; `LoginValidator` returns typed `LoginFieldError`s; `LoginFeatureImpl` registers one
destination with no callbacks. Registration is in the host's `App` (feature-registrations block).

## 6. Host integration

**AUTH-13** — Navigation follows the session state: `SessionHostExtension` combines `SessionShell.destination`
with the back stack and applies `SessionPolicy.route` — no session → the sign-in route (`LoginRoutes`) as the new root (closing
the menu, clearing a screen banner, UI-25); a session while on sign-in → the graph's start destination. *Why:* a
one-shot event delivered to a dead activity is lost; state is re-evaluated after recreation and process restore.
<!-- origin: arch-spec AUTH-13 -->

**AUTH-14** — `SessionPolicy.noticeFor` raises a notice only when a live session ends, preferring the precise
`SessionEndReason`, silent for explicit sign-out and cold start; the extension renders it in the outermost
banner layer (`order = 10`); the next session clears it. *Why:* explain an unexpected sign-in screen, without
nagging on every signed-out launch.
<!-- origin: arch-spec AUTH-14 -->

**AUTH-15** — Features swallow their `SessionExpired` rung (stop loading, show nothing, no navigation).
*Why:* the host is already navigating (DATA-18).
<!-- origin: arch-spec AUTH-15 -->

Host layer order (lower = outer = higher priority): session 10 · access notices 20 · connectivity 30 · screen
banner (Core, innermost). Notice texts: `app/src/main/res/values/session_strings.xml`.

## 7. Testing

- `AuthTestEnv` wires the real `DefaultAuthManager` over fakes (`FakeAuthService`, `FakeSecureTokenStore`,
  `FakeProfileStore`) on one `StandardTestDispatcher`; seed fakes **before** touching `manager` (its constructor
  launches the cold-start check); the keep-alive is off unless a test passes an enabling config (TEST-16).
- Single-flight: hold the refresh with `refreshGate`, start N callers, assert `refreshCalls == 1`, then release.
- Zombie guard: hold a refresh, sign out / switch user, release, assert `Stale` and nothing written.
- Keep-alive: virtual time (`SchedulerTimeSource` reads the scheduler), `advanceTimeBy` + `runCurrent`; pair
  every "no refresh while idle" with a "touch → exactly one refresh" contrast (TEST-40, TEST-41).
- Classifier: `RetrofitAuthServiceTest` over MockWebServer with the backend's exact bodies.
- Integration: `TokenAuthenticatorTest` builds the production business client with this module's contributions
  and drives it from real threads.
- Keystore crypto: instrumented only (`./gradlew :auth:connectedDebugAndroidTest`, not in the CI gate, TEST-05).

Run: `./gradlew :auth:testDebugUnitTest :login:testDebugUnitTest :login-ui:testDebugUnitTest`.

## 8. Changing it

- **Example contract** (adapt together with the fake backend): `AuthApi`, `AuthDtos.kt`, `AuthErrorCode`, where the
  precise reason lives, `SessionUser` fields, the 400/403 login codes treated as definitive.
- A splash that gates on `resolveSession()` and offers retry on `CONNECTION_ERROR` is an addition in the host;
  routing stays in `SessionPolicy`.
- A 401 that a refresh cannot fix (a missing claim): peek its code in `TokenAuthenticator` and route it elsewhere
  before refresh → retry → expire.
- Never: install the bearer or authenticator on `@AuthClient`; refresh outside the coordinator; persist the access
  token; end a session on a transient failure.
