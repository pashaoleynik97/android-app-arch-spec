---
name: feature-verification
description: >
  ON-DEMAND ONLY. End-to-end verification of a shipped feature on the Android emulator: drive the real app
  through mitmproxy against scripted server responses, then prove each expected behaviour from evidence
  (timestamped request log, uiautomator UI reads, screenshots) instead of asserting it by hand. Use ONLY
  when the developer explicitly asks to verify / QA / "check on the emulator" a task, ticket or feature —
  never proactively, never as part of ordinary implementation work (unit tests and
  `./gradlew testDebugUnitTest` remain the default). Covers: booting the AVD, installing the mitmproxy CA,
  the `_harness/fake_backend.py` fixture that gives a signed-in session with no credentials,
  `tools/drive.sh`, observing timing-based behaviour (polling, TTLs, keep-alive) in minutes rather than
  hours, and how to report results.
---

# Feature verification on the emulator

**Invoke only when asked** (VERIFY-01). The developer says something like *"verify this task on the
emulator"*, *"QA this feature"*, *"check that the 401 handling actually works on a device"*. Nothing here runs
as part of normal implementation: a pass costs minutes of device time and leaves the emulator configured.
Unit tests are the default and stay the default. Rules cite `specs/recommended/mitm-testing.md`.

Related: the **`mitmproxy-testing`** skill owns `.mitm_scripts/` (one scenario per file, the script
template, body / DTO fidelity). This skill is the layer above: how to run a whole pass and produce evidence.
`tools/drive.sh` wraps every device step below (`bash tools/drive.sh help`).

---

## 1. Agree on what "verified" means, before touching anything (VERIFY-02)

Write the checklist first, from the feature's own documentation — a QA / edge-case matrix in the project's docs
if one exists, otherwise the task's acceptance criteria. Each row needs a **falsifiable observation**:

| Weak | Falsifiable |
|---|---|
| "the list refreshes in the background" | "a `GET /v1/items` every 30 s ±2 s while foregrounded, and none while backgrounded" |
| "a background tick doesn't touch the session" | "**no** `POST /v1/auth/refresh` in the flow log after a tick that got a 401" |
| "stale data isn't shown" | "the list is replaced by the error block within ~TTL of the last success" |

Then triage (§6) and tell the developer which rows you can actually run before you start.

## 2. What you can observe (and what you cannot)

| Signal | How | Good for |
|---|---|---|
| Every HTTP request, timestamped | `evidence/mitm.log` (`drive.sh mitm`), harness + scenario log lines; `drive.sh requests` | cadence, absence of a call, retry / refresh behaviour |
| Exact UI content | `drive.sh dump NAME` (uiautomator → flat `[l,t][r,b] 'text'` lines) | text / state assertions — deterministic, unlike reading a screenshot |
| What the user sees | `drive.sh shot NAME`, `drive.sh burst NAME N SEC` | layout, colours, insets, transient notices |
| App-side reasoning | `drive.sh logcat [PATTERN]` (the app's `Reporter` lines) | why a branch was taken |

**Cannot:** anything slower than the session (a real 60-minute TTL — unless shortened, §4), a state the
transport cannot reach (a code path with no wire trigger), gesture-level UX. Say so rather than claiming a
pass.

## 3. Setup (once per emulator)

```bash
bash tools/drive.sh boot <AVD>        # waits for sys.boot_completed; starts the AVD if none is connected
bash tools/drive.sh install           # ./gradlew :app:installDebug — only debug builds trust the proxy CA
bash tools/drive.sh proxy-on          # global http_proxy 10.0.2.2:8080 (the host, seen from the emulator)
bash tools/drive.sh ca                # pushes the mitmproxy CA and prints the install steps
```

**The CA.** On a Play-Store image there is no root, so it goes in as a *user* certificate, which Android
allows only once a screen lock exists (`adb shell locksettings set-pin 1234`). Install it through Settings →
Security → Encryption & credentials → Install a certificate → CA certificate → Downloads. A `file://` VIEW
intent straight to the certificate installer fails under scoped storage — the picker is what grants a
readable URI. Drive that UI deterministically with `drive.sh tap-text "<label>"` (dump → find node by text →
tap its centre), not guessed coordinates. On an AOSP image `adb root` works and the CA can go into the
system store instead.

Verify: Settings lists the CA under User credentials, and the first request appears in `evidence/mitm.log`.
Create the AVD with the screen profile of the devices the app targets — layout findings on a different
density are noise.

## 4. Getting the app into the state under test

Prefer the committed fixture over hand-driving the UI:

```bash
bash tools/drive.sh mitm .mitm_scripts/<path>/<SCENARIO>.py     # scenario FIRST, harness appended last
```

`_harness/fake_backend.py` gives a full signed-in session with **no credentials** (`user@example.test` /
`password` in the example contract), plus knobs that make otherwise-untestable behaviour observable:

- `EXPIRES_IN` — the token lifetime the app is *told* and that is enforced. Drop it to ~60 s and refresh /
  keep-alive / idle-logout behaviour that takes an hour happens in a minute.
- `*_LATENCY` — stall a response so a loader, paging footer or in-flight button lasts long enough to
  screenshot, or to background the app mid-request.
- `DETAIL_FAILURE` — force one of the real refusal codes on every call.
- Pool sizes chosen to exercise paging (three pages, the last one short).

Knobs are edited in the file and hot-reloaded on save (which also resets the harness's state). `drive.sh
mitm` always sets `flow_detail=1` — with `0`, a short-circuiting scenario answers before anything logs and
you are blind — and `connection_strategy=lazy`, so it works before the backend exists.

**Long production constants** (a 60-minute TTL in app code): build a one-off APK with the constant
shortened, keep it aside, and **revert the source immediately** (VERIFY-04):

```bash
python3 - <<'PY'   # portable in-place edit (BSD and GNU sed differ on -i)
import pathlib; p = pathlib.Path("<module>/src/main/java/.../Config.kt")
p.write_text(p.read_text().replace("60.minutes", "45.seconds"))
PY
./gradlew :app:assembleDebug && cp app/build/outputs/apk/debug/app-debug.apk evidence/short-ttl.apk
git checkout -- <module>/src/main/java/.../Config.kt     # source clean again; the APK survives
adb install -r evidence/short-ttl.apk
```

## 5. Running a case

1. Put the app in the starting state and **confirm it** (`drive.sh dump`, not assumption).
2. Switch the scenario (`drive.sh mitm <scenario.py>` restarts mitmdump).
3. Trigger — `drive.sh tap-text / swipe / key`, an adb broadcast for hardware input the emulator lacks,
   or just wait for the next poll.
4. Collect evidence **while the state exists**: a 6-second notice needs `drive.sh burst`, not one shot
   after the fact.
5. Assert against the log / dump, and record the timestamps in the report.

Pitfalls that cost real time (VERIFY-05):
- **Sampling too slowly.** A dump round-trip is ~2 s; a 2 s state slips between samples. Widen the state
  with a latency knob rather than sampling faster.
- **Bursts are not evenly spaced.** Screenshots land when the device answers; read the file times.
- **Leftover state from the previous case.** A refusal may have paused a loop, a notice may still be up.
  Re-establish the starting state between cases, or make the order explicit.
- **Your own taps are input.** They count as user activity for anything activity-gated. When testing idle
  behaviour, do not touch the device — dumps are safe, `input tap` is not.

## 6. Triage before you promise (VERIFY-02)

Sort the checklist into: **runnable now** · **needs a new scenario** (write it into the committed library
with its `.md`, per `mitmproxy-testing` — never a throwaway script for something reusable, VERIFY-06) ·
**needs a temporary constant change** · **blocked** (needs data or hardware you do not have). Give the
developer that split up front and ask, rather than silently skipping rows.

## 7. Reporting (VERIFY-03)

Use [references/results-table.md](references/results-table.md). One table, one row per case, with the
**evidence in the row** — a timestamp, a count, bounds, a filename — never a bare "pass". Then:

- **Findings** — what was observed, the root cause in the code, a suggested fix; say whether it is in scope
  or pre-existing.
- **Not verified**, and why. This keeps the report honest.
- **What the emulator was left holding** (proxy, PIN, CA, mitmdump running, signed-in state, a one-off APK)
  and the undo commands (VERIFY-04).

Two habits that pay off: a **contrast pair** proves an invariant far better than a single run (*a
background tick → 0 refreshes* next to *a user pull → exactly 1*), and **absence of a request** is often the
strongest evidence available — but only over a window long enough to be sure.

Evidence goes in the task's folder: `evidence/NN-<case>.png`, `NN-<case>.txt` (flattened dump),
`mitm.log` (raw), `request-log.txt` (only the timestamped request lines, grouped per run — `drive.sh
requests`), logcat extracts. Do not commit evidence; it may hold screenshots of real data.
