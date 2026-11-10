# Recommended: mitm-testing — response mocking and emulator verification (MITM, VERIFY)

Tier: **Recommended** (component `mitm-testing`). SHOULD / MAY guidance with a *why* per rule.
Placeholders: `{{BASE_URL_DEBUG}}`, `{{HEALTH_PATH}}`, `{{APPLICATION_ID}}`.

The component ships: a debug-only network security config, the `.mitm_scripts/` scenario library (one
example scenario + a generic stateful fake backend), `tools/drive.sh` (emulator helper), and two target
skills — `mitmproxy-testing` (optional, model-invocable) and `feature-verification` (on-demand only).

## When to use

- The app talks to an HTTP backend and handles its failures in more than one way (error states, refresh,
  retry, paging, idempotent writes). Unit tests cover the logic; this covers "does the real app, over the real
  wire, do it".
- The backend is shared, unstable, or not built yet — the fake backend gives a signed-in session with no
  credentials and no server.
- You want evidence for behaviour that is slow in production (token expiry, polling, keep-alive) in minutes.

## When not to

- No backend (offline-only app): nothing to intercept.
- Certificate pinning in debug builds: interception needs the pin disabled in the debug variant first.
- Instrumented UI-test suites already cover the same branches with a mock web server — then this is a
  manual / exploratory supplement at most.

Tools needed only to *use* it (never to build): `mitmproxy` (`mitmdump`), `python3`, `adb`, an AVD.

---

## 1. Interception (MITM)

**MITM-01** — User-CA trust SHOULD exist only in the `debug` source set: `app/src/debug/res/xml/
network_security_config.xml` with `<debug-overrides>` trusting `system` + `user`, attached by
`app/src/debug/AndroidManifest.xml` (`tools:replace="android:networkSecurityConfig"`). No app code is involved
(OkHttp honours the system proxy).
*Why:* since API 24 apps ignore user CAs; opting back in only where `debuggable=true` (and only in a file the
release variant never sees) means a release APK cannot be intercepted, by construction.

**MITM-02** — Scenarios SHOULD live in the committed `.mitm_scripts/<path_slug>/<METHOD>_<CODE>_<scenario>.py`
with a sibling `.md`, one scenario per file. `<path_slug>` is the path without the leading `/`, `/` → `_`, a
path parameter contributing its name without braces (`/v1/items/{itemId}` → `v1_items_itemId`). A dropped
connection is named by an equivalent status when the app treats both alike.
*Why:* a shared, named library turns one developer's investigation into a re-runnable regression check, and
the name alone says which endpoint and which outcome a file is about.

**MITM-03** — A scenario SHOULD match precisely (exact method + exact path, query ignored; a regex only for a
base-path prefix or path parameters), short-circuit in the `request` hook by default, return early when
`flow.response` is already set, and print one timestamped line per match.
*Why:* a loose match catches calls the scenario is not about; short-circuiting works without the real server;
the timestamped line is the only evidence that a short-circuited request happened.

**MITM-04** — Mock bodies SHOULD mirror the app's DTO / envelope contract — or break it deliberately for a
malformed-body scenario, stated in the `.md`. The `.md` SHOULD state the expected behaviour and a falsifiable
"what would be a bug".
*Why:* a body the app cannot parse exercises the malformed-body branch, not the one the scenario claims to
test; a stated negative is what makes a run pass or fail.

**MITM-05** — Multi-endpoint fixtures SHOULD live only in `.mitm_scripts/_harness/` and compose
**scenario first, harness last**: the harness answers only flows no earlier script answered, and only for the
API host (`{{BASE_URL_DEBUG}}`), passing everything else through.
*Why:* the scenario stays the single source of truth for its endpoint, and the harness never has to be forked
per test; ignoring other hosts keeps system traffic out of the evidence log.

**MITM-06** — The fake backend SHOULD be **as strict as the real API**: only tokens it minted are accepted,
expiry is enforced, refresh tokens are single-use, unknown fields are refused (400), state is mutable so a
write is visible on the next read and a transition happens exactly once, all timestamps come from one server
clock, and every handled call logs `[fake-be HH:MM:SS] METHOD path -> code (summary)` with secrets
truncated. It SHOULD ship a pure-Python `--selftest` that exercises every handler and runs without mitmproxy.
*Why:* a lenient fake lets a test pass that should fail — serving any bearer hides a missing context or
refresh step; accepting a reused refresh token hides a missing single-flight refresh. The self-test keeps the
fake honest as it grows, and runs as the install gate.

**MITM-07** — Runs SHOULD use `--set flow_detail=1` and, for flows the harness answers, `--set
connection_strategy=lazy`.
*Why:* with `flow_detail=0` a short-circuited scenario is invisible in the log; with eager connections
mitmproxy dials the real host first, which fails before the backend exists.

**MITM-08** — Committed scripts SHOULD contain synthetic data only (`example.test` addresses, `fake-…`
tokens); evidence folders SHOULD NOT be committed.
*Why:* a token or screenshot captured from a real environment is a credential or personal-data leak once
it is in git history.

## 2. Emulator verification (VERIFY)

**VERIFY-01** — A full verification pass SHOULD run **only on explicit request** ("verify / QA / check on the
emulator"); the `feature-verification` skill's description says so and it is never part of ordinary
implementation work. Projects MAY additionally set `disable-model-invocation: true` on it.
*Why:* a pass costs device minutes and leaves the emulator configured (proxy, PIN, CA); unit tests remain the
default gate. The flag is optional because the trigger is usually a natural-language request the agent must
be able to act on.

**VERIFY-02** — A pass SHOULD start with a checklist of **falsifiable** observations (from the `.docs/` QA
matrix or the acceptance criteria) and a triage — runnable now · needs a new scenario · needs a temporary
constant change · blocked — shared with the developer before running.
*Why:* "the list refreshes" cannot fail; "a `GET` every 30 s ±2 s while foregrounded, none in background"
can. Triage up front avoids silently skipped rows.

**VERIFY-03** — The result SHOULD be one table, a row per case, with the evidence **in the row** (timestamp,
count, bounds, filename), followed by Findings (observed · root cause · fix · scope), Not verified (and why),
and what the emulator was left holding with undo commands. Contrast pairs and absence-of-request evidence
(over a long enough window) are preferred.
*Why:* a bare checkmark is unfalsifiable after the fact; the honest "not verified" list is what keeps the
report trustworthy.

**VERIFY-04** — Temporary changes (a shortened production constant, a debug PIN, the proxy) SHOULD be undone
or listed: a constant is shortened only in a one-off APK and the source reverted immediately.
*Why:* a forgotten 45-second TTL in source ships; a forgotten proxy breaks the next person's emulator.

**VERIFY-05** — Short-lived states SHOULD be widened with harness latency knobs rather than sampled faster,
and idle / activity-gated behaviour SHOULD be observed without touching the device.
*Why:* a UI dump round-trip is about two seconds; taps are user activity and reset idle timers.

**VERIFY-06** — A scenario written for a verification pass that is reusable SHOULD go into the committed
library (with its `.md`), not a scratch folder.
*Why:* the next change to the same endpoint needs the same check.

## 3. Tooling

`tools/drive.sh` (bash, macOS + Linux) wraps every step: `boot`, `install`, `proxy-on/off`, `ca`, `mitm
<scenario…>` (scenario first, harness appended, log to `evidence/mitm.log`), `requests`, `dump` (flattened
`[l,t][r,b] 'text'`), `find` / `tap-text` (find a node by text, tap its centre), `shot` / `burst`, `logcat`
(app process, the `Reporter` lines). Hardware input the emulator lacks is driven by a debug-only adb
broadcast listener (see the `hardware-input` component), never by test-only code in `src/main`.

## Adapting this to your app

- **Example-only:** the harness's contract — paths (`/v1/auth/*`, `/v1/items*`), the `{"data"}` /
  `{"error":{"error","message","details"}}` envelope, credentials, item shapes, the `Idempotency-Key` header.
  Mirror your `network` / `auth-session` DTOs: change `_ok` / `_error`, the `ROUTES` table and the handlers,
  and extend `--selftest`. Delete `v1_items/GET_500_example-server-error.*` once you have real scenarios.
- **Keep:** scenario-first composition, strictness (MITM-06), timestamped logging, the self-test, debug-only
  trust.
- Health path and API host come from `{{HEALTH_PATH}}` and `{{BASE_URL_DEBUG}}`; re-render
  (`render.py rerender BASE_URL_DEBUG`) when the base URL changes, or edit `BASE_URL` in the harness.
- Emulator proxy `10.0.2.2:8080` is the standard emulator → host alias; a physical device needs the host's
  LAN address and the CA installed the same way.
