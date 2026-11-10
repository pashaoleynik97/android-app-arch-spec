# Recommended: auth-session — session, tokens, refresh, sign-in (AUTH)

Tier: **Recommended** (component `auth-session`, depends on `network`). SHOULD / MAY guidance with a *why*.
No parameters of its own: it reuses `NetworkConfig` from `network`.

Related: ADR-0008 (separate clients), ADR-0009 (activity-gated keep-alive, the server decides session death),
`../60-data-and-errors.md` DATA-17 … DATA-19 (ambiguous failures, host-owned outcomes, refresh policy).

## When to use

- The backend issues an access token plus a **refresh token** (rotating, often single-use) and the app must
  stay signed in across process restarts.
- Several requests can hit 401 at once (lists, polls, parallel loads) and a false logout would be costly.

## When not to

- Session cookies managed by the server, or an SDK (Firebase Auth, an OIDC library) that owns tokens — keep
  the rules about classification, host-owned navigation and backup exclusion; drop the manager.
- No sign-in at all.

## What it adds

`:auth` (`{{ROOT_PACKAGE}}.auth`, no UI): `AuthManager` / `AuthState` / `SessionResolution` / `LoginOutcome` /
`SessionEndReason`, `DefaultAuthManager` (single-flight refresh, zombie guard, keep-alive), `RetrofitAuthService`
(the classifier), `AuthInterceptor` + `TokenAuthenticator` (contributed to the network client's seams), the
session's own `@AuthClient`, `KeystoreSecureTokenStore`, `SharedPrefsProfileStore`, `UserActivityTracker`,
`SessionShell` + `SessionPolicy`. A minimal sign-in trio `:login-ui-api` / `:login-ui` / `:login`. In `:app`:
`session/SessionHostExtension` + `di/SessionHostModule`, `res/values/session_strings.xml`, and the session-file exclusions inserted into
Core's Auto Backup rule files (`res/xml/backup_rules.xml`, `data_extraction_rules.xml`) through their marker blocks.

---

## 1. Session model

**AUTH-01** — The session SHOULD have one source of truth, `AuthManager.authState: StateFlow<AuthState>`
(`Initializing` · `Authenticated(userId, accessToken)` · `Unauthenticated(reason, sessionEndReason?)`), with
`Authenticated` re-emitted on every rotation and lock-free `current…()` reads.
*Why:* every consumer (host navigation, sockets, caches keyed by user) derives from one flow; lock-free reads let
the bearer interceptor run on every request without waiting for a refresh.

**AUTH-02** — Only the **refresh token** SHOULD be persisted, encrypted with AES-GCM under an Android Keystore
key in a dedicated preferences file; the access token stays in memory. Decrypt failure degrades to "no session"
(KERNEL-17). `EncryptedSharedPreferences` is not used.
*Why:* the access token is short-lived and re-obtainable; a Keystore key never leaves the device; a lost key
(lock-screen reset, restore) must read as "sign in again", never as a crash loop.

**AUTH-03** — The token and profile preference files SHOULD be excluded from Auto Backup **and** device transfer,
and the exclusion SHOULD sit next to a comment naming the store that writes the file.
*Why:* restored onto another device, Keystore-encrypted data can never be decrypted, and a restored profile
without its session shows a stranger's name.

**AUTH-04** — The cold-start check (`resolveSession`) SHOULD leave the state `Initializing` and answer
`CONNECTION_ERROR` on a transient failure, keeping the stored token for a retry.
*Why:* flaky Wi-Fi at launch must never land a signed-in user on the sign-in screen.

## 2. Refresh

**AUTH-05** — Refresh SHOULD be **single-flight**: one `Mutex`, one shared in-flight `Deferred` started on the
module's app-lifetime scope, an in-lock double-check that returns the current token when the caller's token was
already rotated, and the in-flight slot cleared in `finally` under `NonCancellable`.
*Why:* refresh tokens are single-use — two concurrent refreshes make the server refuse one and the user is
logged out; running on the app scope keeps one cancelled caller from cancelling the refresh others await; a
cancelled caller that cannot clear the slot leaves a completed `AuthFailed` parked there for the rest of the
process.

**AUTH-06** — Every identity change (login, logout, refused refresh, end of session) SHOULD bump a session
generation; a refresh SHOULD check it **before** going to the network and **again before** writing its result,
answering `Stale` on a mismatch.
*Why:* a late answer must never resurrect a signed-out session or overwrite another user's; checking before the
call also avoids spending a single-use token that the newer session holds.

**AUTH-07** — Only a **definitive** refusal (401 on refresh, a known login refusal code) SHOULD end a session;
5xx, IO, timeouts, unknown 4xx, a 2xx without a body or with a blank token are `Transient` and keep it.
*Why:* DATA-17 — a false logout is the most expensive bug this layer can produce.

**AUTH-08** — The business client's `Authenticator` SHOULD route every 401 into the coordinator (bridged with
`runBlocking` on OkHttp's thread), retry **once**, expire the session on a second 401 for the caller's token
only, and skip requests tagged `RefreshPolicy.NeverRefresh`.
*Why:* OkHttp calls authenticators concurrently and does not single-flight them; one retry stops loops; the
policy keeps background work from extending (or ending) a session (DATA-19).

**AUTH-09** — Login, refresh and logout SHOULD use the module's own client (`@AuthClient`) with no bearer
interceptor and no authenticator, on the same base URL and `Json`.
*Why:* a 401 on refresh must never trigger another refresh (ADR-0008).

## 3. Keep-alive and sign-out

**AUTH-10** — A proactive refresh SHOULD run shortly before expiry **only if the user interacted since the last
token commit**; otherwise the loop waits on the activity tracker (never returns, never ends the session). The
server's idle window decides session death (ADR-0009).
*Why:* an actively used session must not lapse mid-task, an abandoned one must not be kept alive forever, and a
returning user's first touch must be able to revive the loop.

**AUTH-11** — User activity SHOULD be stamped by the host, not by screens: the session host extension observes
every pointer-down in the window (initial pass, never consumed). Apps driven by hardware keys also stamp from
their key handling.
*Why:* a stamp per screen is forgotten on the next screen; observing without consuming cannot break any gesture.

**AUTH-12** — Sign-out SHOULD be local-first: a best-effort remote revoke with a few retries (any answer below
500 counts as done), then **always** the local wipe. The non-secret profile cache MAY survive sign-out.
*Why:* a user who taps "sign out" offline must be signed out; a revoke that cannot reach the server must not
keep tokens on the device.

## 4. Host integration

**AUTH-13** — Navigation SHOULD follow the session **state**, not events: the host extension combines
`SessionShell.destination` with the back stack and applies `SessionPolicy.route` (no session → sign-in as the new
root; a session while on sign-in → the start destination), closing the menu and clearing screen banners when it
re-roots (UI-25).
*Why:* a one-shot "go to sign-in" event delivered to a dead activity is lost; state is re-evaluated after
recreation and process restore.

**AUTH-14** — A notice SHOULD be raised only when a **live** session ends, choosing the backend's precise
`SessionEndReason` over the coarse reason, silent for an explicit sign-out and a cold start; it is shown in the
outermost host banner layer and cleared by the next session.
*Why:* the user needs to know why they are suddenly on the sign-in screen, but not every time they open the app
signed out.

**AUTH-15** — Features SHOULD swallow their `SessionExpired` rung (stop spinners, show nothing) and never
navigate on it (DATA-18).
*Why:* the host is already navigating; two reactions produce double navigation and duplicate banners.

**AUTH-16** — The sign-in screen SHOULD hold the password only in the ViewModel (never in published UI state),
validate on submit, keep the input on `Unavailable`, clear the password on a refusal, and do no navigation of
its own.
*Why:* KERNEL-05; ambiguous failures keep user input (DATA-17); the session state decides where the user goes.

---

## Adapting this to your app

- **Example contract — adapt in code:** endpoints and DTOs (`network/AuthApi.kt`, `network/dto/AuthDtos.kt`:
  `email`/`password`, `accessToken`/`refreshToken`/`expiresIn`, `user{id,email,displayName}`), the error codes
  (`AuthErrorCode`), where the precise reason lives (`details.reason`), `SessionUser`'s fields. Keep the
  mitm-testing fake backend in step.
- **Sign-in UI:** the shipped trio is deliberately minimal (email + password). Replace its screen freely; keep
  AUTH-16 and let the session host extension do the navigation.
- **Splash:** the start destination shows while `Initializing`. If that flashes content, add a splash route that
  calls `resolveSession()` and offers retry on `CONNECTION_ERROR` — the routing stays in `SessionPolicy`.
- **Sign-out button:** call `LoginInteractor.signOut()` from wherever the app offers it (an app-menu option, a
  settings screen). With `connectivity` installed you MAY skip the remote revoke when clearly offline.
- **401s a refresh cannot fix** (e.g. "token lacks a required claim"): peek the code in `TokenAuthenticator` and
  route it elsewhere instead of refresh → retry → expire.
- **Tuning:** `ProactiveRefreshConfig` (lead time, retry delay) is provided by `AuthModule`.
- **Keep:** single-flight + generation guard + `NonCancellable` cleanup, definitive-only session death,
  refresh-token-only Keystore storage, backup exclusions, state-driven host navigation.
