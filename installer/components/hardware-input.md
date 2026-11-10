# Component: hardware-input (Recommended)

Rules: `specs/recommended/hardware-input.md` (INPUT-01 … INPUT-14). Shorthands as in `INSTALL.md`:
`SPEC=.arch-spec/spec`, `R` = `python3 $SPEC/installer/render.py --target .`

## What it adds

| Module / file | Purpose |
|---|---|
| `:input-api` (`{{ROOT_PACKAGE}}.input.api`) | driver contract: `InputDriver`, `InputDriverProvider`, `InputEvent`, `InputDriverState`, `Detection`, `isPackageInstalled`; `README.md` with **Adding a vendor module** |
| `:input` (`{{ROOT_PACKAGE}}.input`) | `InputController` (Activity lifecycle via the host's `HostActivityObserver` set), `InputDriverResolver`, `InputPreference` + codec, settings store / repository, `CollectInputWhileResumed`, `InputModule` (`@Multibinds` providers) |
| `:input-debug` (`{{ROOT_PACKAGE}}.input.debug`) | adb-broadcast driver at priority −100 — `debugImplementation` only |
| tests in all three modules | resolver, controller, repository, codec, event equality, debug-event parsing |
| `.claude/skills/application-architecture/references/hardware-input.md` | rules, usage, testing |

Marker edits: three `include(...)` lines; `implementation(project(":input"))` and
`debugImplementation(project(":input-debug"))` in `:app`; skill index line; `CLAUDE.md` `components` + `modules`.
No vendor driver is shipped: vendors are added per device family (`input-api/README.md`).

With the `engineering-menu` component installed (before or after this one), its "Hardware input" section compiles
automatically.

## Prerequisites

- `architecture` installed (the controller uses the Core `HostActivityObserver` seam).

## Questions (all skippable)

| Parameter | Ask | Safe placeholder |
|---|---|---|
| `APPLICATION_ID` | not asked — from the project (used in the documented adb action) | — |

Ask instead, for the report: "Which input hardware and vendors will the app support?" — record the answer; each
becomes a vendor module later (not part of this install).

## Install

```
R plan hardware-input
R install hardware-input
```

No manual steps. Try it on an emulator (debug build, app in front):
```
adb shell am broadcast -a <applicationId>.input.DEBUG_EVENT --es data "4006381333931" --es kind "ean13"
```
`logcat` (debug build) shows one `InputController` line per reading.

## Gate

```
./gradlew assembleDebug assembleRelease
./gradlew :input-api:testDebugUnitTest :input:testDebugUnitTest :input-debug:testDebugUnitTest
```

`assembleRelease` proves the release build compiles without `:input-debug` (empty driver set). Optional:
`./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep input-debug` prints nothing.

## Completing deferred items

Nothing is deferred. Adding a vendor later: `input-api/README.md`, six steps.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `MissingBinding: Set<InputDriverProvider>` | `InputModule` lost its `@Multibinds` declaration — restore it |
| Auto never picks the vendor driver on the device | the vendor module's manifest lacks `<queries><package …/></queries>`, or the provider's priority is not above −100 |
| adb broadcasts do nothing | release build installed, app not in the foreground (receiver registered only while resumed), wrong action (it follows the installed package name, suffix included), or the driver is not the active one (check the engineering menu / logcat `input driver ->`) |
| `SecurityException: … RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED` | a runtime receiver registered without the flag — use `ContextCompat.registerReceiver(…, flag)` |
| A screen in the back stack reacts to readings | it collects `events` directly instead of through `CollectInputWhileResumed` |
| Readings arrive twice | normal for some hardware — make the handler idempotent (INPUT-12) |

## Commit message

`chore(arch-spec): hardware-input [spec vX.Y.Z]`
