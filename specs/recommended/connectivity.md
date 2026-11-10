# Recommended: connectivity — can we reach the backend? (CONN)

Tier: **Recommended** (component `connectivity`, depends only on `architecture`). SHOULD / MAY guidance.
Placeholders: `{{BASE_URL_DEBUG}}`, `{{BASE_URL_RELEASE}}`, `{{HEALTH_PATH}}`.

## When to use

- Users work where the network comes and goes (field work, transit, large buildings) and should be told
  before an action fails, not after.
- The backend has (or can get) a cheap unauthenticated health endpoint.

## When not to

- Fully offline-first apps with background sync — they need a sync-status model, not a banner.
- No backend, or no health endpoint and no way to add one (an OS-only "online" signal lies behind captive
  portals; the component would then only report link state).

## What it adds

`:connectivity` (`{{ROOT_PACKAGE}}.connectivity`, no UI, no dependency on `network`): `NetworkState`,
`NetworkStateWatch` + `DefaultNetworkStateWatch`, `ConnectivityMonitor` (OS default-network callback),
`ServerHealthProbe` on its own `@ConnectivityClient`, the pure `NetworkStateResolver`, `ConnectivityConfig` (from
its own `BuildConfig`), and the banner logic `ConnectivityBannerPolicy` + `ConnectivityBannerController`. In
`:app`: `connectivity/ConnectivityBannerExtension`, `di/ConnectivityHostModule`, `res/values/connectivity_strings.xml`.

---

**CONN-01** — The app SHOULD have one `NetworkStateWatch` singleton — `UNKNOWN` · `ONLINE` ·
`SERVER_UNREACHABLE` · `OFFLINE` — combining the OS default network's internet capability with a server health
probe; consumers inject it, nobody else probes.
*Why:* "Wi-Fi is up" is not "the server answers" (captive portals, DNS, a down backend); one watch means one
answer for every screen and one probe at a time.

**CONN-02** — The probe SHOULD use its own client: unauthenticated, short timeouts (seconds), no interceptors,
`GET <base URL><health path>`; any 2xx is reachable, anything else (and any IO failure) is not.
*Why:* a probe that depends on session state reports "offline" when the token expired; a slow probe is already
an answer (ADR-0008).

**CONN-03** — The mapping SHOULD be a pure function of (link, probe answer), tested as a full truth table; no
link → `OFFLINE` immediately, without a probe.
*Why:* the decision is the contract; a pure table makes every combination a checked case, and skipping the probe
without a link saves a guaranteed timeout.

**CONN-04** — Triggers (link change, `flush()`) SHOULD go through one `flatMapLatest`, so a newer trigger cancels
a probe in flight; the state only moves on the offline short-circuit or a **completed** probe.
*Why:* a stale probe answer must not land after a fresh one; never resetting on a trigger is what lets `flush()`
keep the current value until the new answer (no flicker through `UNKNOWN`).

**CONN-05** — `flush()` SHOULD be conflated (a burst costs one probe) and there SHOULD be no periodic polling.
*Why:* the OS reports link changes; the user asks for a re-probe; a timer drains battery and hits the backend
from every idle device.

**CONN-06** — The banner rules SHOULD be pure and tested: show *Offline* on **entering** a problem state
(`OFFLINE` or `SERVER_UNREACHABLE`), *Restored* only when leaving it for `ONLINE`, clear when leaving it for
`UNKNOWN`, and keep a banner the user closed closed while the problem lasts.
*Why:* re-raising on every problem emission nags a user who already dismissed it; announcing "restored" for an
unknown state is a lie.

**CONN-07** — The banner SHOULD be rendered by a host extension layer (below session and access notices, above
screen banners); *Offline* carries retry + close and does not time out, *Restored* has no close and times out;
retry re-probes once and spins a minimum time.
*Why:* connectivity is host-level — a per-screen banner duplicates and disappears on navigation; priority by
layer needs no arbitration (UI-26); a retry that visibly does nothing for a fast unchanged answer reads as broken.

**CONN-08** — Code that must decide *before* a call (skip a best-effort remote revoke, defer a sync) MAY read
`getCurrentNetworkState()`; it SHOULD NOT replace classifying the call's own result.
*Why:* the watch is advisory and lags reality by up to one probe; the call's own outcome is authoritative.

---

## Adapting this to your app

- **Environment:** `{{HEALTH_PATH}}` (relative to the base URL) and the base URLs are rendered into
  `connectivity/build.gradle.kts`; re-render with `render.py rerender BASE_URL_DEBUG BASE_URL_RELEASE HEALTH_PATH`.
  To share the URL with `network` from one place, move `ConnectivityConfigModule` to `:app/di/` and build the
  config from `NetworkConfig` there.
- **Degraded states:** an app that must warn on metered or cellular-only links adds a state and a column to the
  resolver's table; keep the table test exhaustive.
- **Copy and style:** the banner texts live in `app/src/main/res/values/connectivity_strings.xml`.
- **Keep:** one watch, own unauthenticated probe client, pure resolver + pure banner policy, `flatMapLatest`
  supersession, no polling.
