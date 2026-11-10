# Connectivity — can we reach the backend? (component: connectivity)

Recommended guidance (SHOULD) from `.arch-spec/spec/specs/recommended/connectivity.md` (`CONN-*`). Module `:connectivity`
(`{{ROOT_PACKAGE}}.connectivity`, no UI, independent of `:network`); banner layer `ConnectivityBannerExtension`
in the `connectivity` package of `:app`.

Contents: 1. Surface · 2. Rules · 3. Using it · 4. Testing · 5. Changing it

---

## 1. Surface

| Type | Role |
|---|---|
| `NetworkStateWatch` | `networkState: StateFlow<NetworkState>`, `getCurrentNetworkState()`, `flush()` |
| `NetworkState` | `UNKNOWN` · `ONLINE` · `SERVER_UNREACHABLE` · `OFFLINE` |
| `ConnectivityBannerController` | host banner as state (`banner`, `retrying`), `onRetry()`, `onDismiss()` |
| `ConnectivityBannerPolicy` | pure banner transitions |
| `ConnectivityConfig` | `baseUrl` + `healthPath`, from `connectivity/build.gradle.kts` |

Internal: `ConnectivityMonitor` (default-network callback), `ServerHealthProbe` (`@ConnectivityClient`, 5 s),
`NetworkStateResolver` (pure), `DefaultNetworkStateWatch` (the driver on `@ConnectivityScope`).

## 2. Rules

**CONN-01** — One `NetworkStateWatch` combines the OS link (default network has internet capability) with a
server health probe; consumers inject it, nobody else probes. *Why:* a link is not a reachable server (captive
portals, DNS, a down backend); one watch = one answer and one probe at a time.
<!-- origin: arch-spec CONN-01 -->

**CONN-02** — The probe runs on its own client: unauthenticated, seconds-long timeouts, no interceptors; any 2xx
is reachable, anything else or any IO failure is not. *Why:* a probe tied to the session reports "offline" when a
token expires (ADR-0008).
<!-- origin: arch-spec CONN-02 -->

**CONN-03** — (link, probe answer) → state is the pure `NetworkStateResolver`, tested as a full table; no link →
`OFFLINE` at once, no probe. *Why:* every combination is a checked case; probing without a link is a guaranteed
timeout.
<!-- origin: arch-spec CONN-03 -->

**CONN-04** — Link changes and `flush()` go through one `flatMapLatest` (a newer trigger cancels the probe in
flight); the state moves only on the offline short-circuit or a completed probe. *Why:* no stale answer after a
fresh one, and `flush()` keeps the current value until the new answer (no flicker through `UNKNOWN`).
<!-- origin: arch-spec CONN-04 -->

**CONN-05** — `flush()` is conflated and there is no polling. *Why:* the OS reports link changes and the user asks
for a re-probe; a timer drains battery and hits the backend from every idle device.
<!-- origin: arch-spec CONN-05 -->

**CONN-06** — Banner rules (`ConnectivityBannerPolicy`): *Offline* on entering a problem (`OFFLINE`,
`SERVER_UNREACHABLE`); *Restored* only when leaving it for `ONLINE`; clear when leaving it for `UNKNOWN`; a banner
the user closed stays closed while the problem lasts. *Why:* no nagging after a dismissal, no false "restored".
<!-- origin: arch-spec CONN-06 -->

**CONN-07** — The banner is a host layer (`order = 30`): below session and access notices, above screen banners.
*Offline* has retry + close and no timeout; *Restored* has no close and times out; retry re-probes once and spins
at least `ConnectivityBannerController.MIN_RETRY_SPIN_MILLIS`. *Why:* connectivity is host-level (UI-26); a
retry that visibly does nothing reads as broken.
<!-- origin: arch-spec CONN-07 -->

**CONN-08** — Code that decides before a call (skip a best-effort remote call when clearly offline) may read
`getCurrentNetworkState()`; it never replaces classifying the call's own result. *Why:* the watch is advisory
and lags by up to one probe.
<!-- origin: arch-spec CONN-08 -->

## 3. Using it

- A screen never shows its own offline banner; the host layer does. A screen MAY disable a "send" button while
  `networkState` is `OFFLINE` — through its interactor (DATA-02), not by injecting the watch into the ViewModel.
- Retry on a failed request is the feature's own `Transient` path; the banner's retry is only a re-probe.
- The watch starts when `ConnectivityBannerController` is first injected (the host extension, at activity
  start). Code that needs it earlier injects `NetworkStateWatch` into its own eager singleton.
- Texts: `app/src/main/res/values/connectivity_strings.xml`.

## 4. Testing

- `NetworkStateResolverTest` — the whole table.
- `DefaultNetworkStateWatchTest` — fakes `FakeConnectivityMonitor` (`set(link)`) and `FakeServerHealthProbe`
  (`reachable`, `calls`, `gate`); flush keeps the value until the answer, a burst costs one probe, a newer trigger
  cancels the probe in flight.
- `OkHttpServerHealthProbeTest` — MockWebServer: path relative to the base URL, 503 and a dropped connection.
- `ConnectivityBannerTest` — the policy table and the controller (closed stays closed, retry spin).
- Device: the `GET_503_server-unreachable` scenario under `.mitm_scripts/{{HEALTH_PATH_SLUG}}/`.

Run: `./gradlew :connectivity:testDebugUnitTest`.

## 5. Changing it

- Environment: the base URLs and the health path are rendered into `connectivity/build.gradle.kts`
  (`render.py rerender BASE_URL_DEBUG BASE_URL_RELEASE HEALTH_PATH`). To share one URL with `:network`, move
  `ConnectivityConfigModule` into the host's `di` package and build the config from `NetworkConfig`.
- A degraded state (metered, cellular-only) = a new `NetworkState` value, a new column in the resolver table, a
  decision in the banner policy table.
- Keep: one watch, the unauthenticated probe client, pure resolver and policy, `flatMapLatest`, no polling.
