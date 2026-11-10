# Verification results — template

Copy into the task folder as `verification.md` and fill in. Every row carries its evidence; a row without
evidence is "not verified", not "pass".

```markdown
# Verification — <feature / task>

- **Build:** debug, commit `<sha>` (or one-off APK `evidence/<name>.apk`: <constant> shortened to <value>)
- **Device:** <AVD name>, API <level>, <W>x<H> dp
- **Harness:** `_harness/fake_backend.py` knobs changed: `EXPIRES_IN=60`, … (or "defaults")
- **Date / window:** <date>, <HH:MM>–<HH:MM>

## Results

| # | Case (falsifiable expectation) | Scenario(s) | Result | Evidence |
|---|---|---|---|---|
| 1 | `GET /v1/items` every 30 s ±2 s while foregrounded | harness only | pass | `request-log.txt` 10:02:01, :31, 10:03:01 (3 calls, 30.0 s apart) |
| 2 | no `POST /v1/auth/refresh` after a background-tick 401 | `v1_items/GET_401_…` | pass | `mitm.log` 10:05:00–10:09:00: 0 refresh lines |
| 3 | error block with Retry after a 500 | `v1_items/GET_500_example-server-error` | FAIL | `03-error.png`, `03-error.txt`: `[16,200][344,240] 'Nothing here'` — empty state shown instead |
| 4 | … | … | not verified | blocked: needs <hardware / data> |

## Findings

1. **<short title>** — observed: <what, with evidence ref>. Root cause: `<file>:<line>` <why>. Suggested fix:
   <one line>. Scope: in scope | pre-existing | out of scope.

## Not verified

- <case> — <why: no wire trigger / slower than the session / needs a device>.

## Emulator left holding

- proxy `10.0.2.2:8080` → `bash tools/drive.sh proxy-off`
- screen-lock PIN 1234 → `adb shell locksettings clear --old 1234`
- mitmproxy user CA → Settings → Security → User credentials → remove
- mitmdump running → `bash tools/drive.sh mitm-stop`
- signed in as `user@example.test` (harness session) → `bash tools/drive.sh clear-data`
- one-off APK installed → `bash tools/drive.sh install`
```

## Evidence folder layout

```
evidence/
  01-<case>.png        screenshot(s); bursts as 01-<case>-01.png …
  01-<case>.txt        flattened uiautomator dump (drive.sh dump)
  01-<case>.xml        raw dump, when bounds / hierarchy matter
  mitm.log             raw mitmdump output, appended across runs (each run starts with a "=== HH:MM:SS" line)
  request-log.txt      timestamped request lines only (drive.sh requests > evidence/request-log.txt)
  <case>.log           logcat extract (drive.sh logcat-dump)
```
