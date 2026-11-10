# Component: mitm-testing (Recommended)

Rules: `specs/recommended/mitm-testing.md` (MITM-01 … MITM-08, VERIFY-01 … VERIFY-06).

## What it adds

| File | Purpose |
|---|---|
| `app/src/debug/res/xml/network_security_config.xml` | debug-only trust of user CAs (`<debug-overrides>`) |
| `app/src/debug/AndroidManifest.xml` | attaches it to debug builds only (`tools:replace`) |
| `.mitm_scripts/README.md` | the library's layout and naming |
| `.mitm_scripts/_harness/fake_backend.py` + `README.md` | generic strict fake backend (example contract) with `--selftest` |
| `.mitm_scripts/v1_items/GET_500_example-server-error.{py,md}` | the canonical scenario template |
| `tools/drive.sh` | emulator helper: boot, install, proxy, CA, mitm switch, dump / find / tap-text, screenshots, logcat |
| `evidence/.gitignore` | keeps verification evidence out of git |
| `.claude/skills/mitmproxy-testing/SKILL.md` | optional skill: writing scenarios |
| `.claude/skills/feature-verification/SKILL.md` + `references/results-table.md` | on-demand skill: full emulator pass |

No Gradle module and no app code: OkHttp honours the system proxy.

## Prerequisites

- `architecture` installed (provides `:app` with its main manifest).
- To *use* it (not to build): `python3`, `mitmproxy` (`mitmdump`), `adb`, an AVD. Missing tools only skip the
  self-test gate — report it.

## Questions (all skippable)

| Parameter | Ask | Safe placeholder |
|---|---|---|
| `BASE_URL_DEBUG` | usually already answered for `network`; the harness only answers this host | `https://api.example.invalid/` (deferred `network.baseUrl`) |
| `HEALTH_PATH` | usually answered for `connectivity` | `health` |
| `APPLICATION_ID` | not asked — from the project | — |

## Install

```
R plan mitm-testing
R install mitm-testing
```

If the project already has `app/src/debug/AndroidManifest.xml` (exit code 2 → `.arch-spec-new`), merge by hand:
keep the existing content and add `android:networkSecurityConfig="@xml/network_security_config"` plus
`tools:replace="android:networkSecurityConfig"` to its `<application>` element.

Tell the user (manual, optional, never done by the installer): `brew install mitmproxy` / `pipx install
mitmproxy`; run `mitmdump` once to generate the CA; then `bash tools/drive.sh help`. Installing the CA on an
emulator needs a screen-lock PIN — the `feature-verification` skill walks through it.

**Hand to the user, don't apply yourself:** the device-testing commands for the project's Claude Code allow-list.
An agent must not edit its own permission settings, and Claude Code blocks it anyway. List these entries in the
report for the user to merge into `permissions.allow` in `.claude/settings.json`:
```json
"Bash(adb shell uiautomator dump *)", "Bash(adb exec-out screencap *)", "Bash(adb logcat -d *)",
"Bash(bash tools/drive.sh *)", "Bash(mitmdump *)", "Bash(python3 -I .mitm_scripts/_harness/fake_backend.py *)"
```
Device-mutating commands (`adb shell input`, `adb shell settings put`, `adb install`) stay unlisted on purpose, so
each one asks first.

The example contract in `fake_backend.py` is to be adapted to the real API (docstring + `ROUTES`), together
with `--selftest`; say so in the report.

## Gate

```
python3 -I .mitm_scripts/_harness/fake_backend.py --selftest
./gradlew :app:assembleDebug
```

Optional: `bash -n tools/drive.sh`; a release build must not contain the debug config
(`./gradlew :app:processReleaseMainManifest` and check the merged manifest has no `networkSecurityConfig`
from this component).

## Completing deferred items

- **`network.baseUrl`** (`BASE_URL_DEBUG`): `R set BASE_URL_DEBUG=https://…/`, `R rerender BASE_URL_DEBUG`
  re-renders `fake_backend.py` if unmodified; if it was adapted, edit its `BASE_URL` constant by hand. Re-run
  the self-test.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Manifest merger: `tools:replace` / attribute conflict | the main manifest or another library sets a different config — merge it into the debug XML, keep `tools:replace` |
| The app ignores the proxy | release build installed (only debug trusts user CAs), or proxy not set (`drive.sh proxy-on`) |
| TLS errors in `mitm.log` | CA not installed as a user CA, or the app pins certificates in debug |
| Every call `UNHANDLED` | `BASE_URL` in the harness does not match the host / base path the app calls |
| `connection refused` / DNS errors upstream | run with `--set connection_strategy=lazy` (`drive.sh mitm` does) |
| Self-test fails after editing the harness | a handler changed without updating `_selftest` — fix one or the other |

## Commit message

`chore(arch-spec): mitm-testing [spec vX.Y.Z]`
