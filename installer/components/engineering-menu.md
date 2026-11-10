# Component: engineering-menu (Recommended)

Rules: `specs/recommended/engineering-menu.md` (ENG-01 … ENG-10). Shorthands as in `INSTALL.md`:
`SPEC=.arch-spec/spec`, `R` = `python3 $SPEC/installer/render.py --target .`

## What it adds

| Module / file | Purpose |
|---|---|
| `:engineering-ui-api` (`{{ROOT_PACKAGE}}.engineering.ui.api`) | `EngineeringFeatureApi`, `EngineeringRoutes.MENU`, the `EngineeringSection` / `EngineeringRow` / `EngineeringControl` contract, `TapSequenceDetector` (hidden-gesture helper) |
| `:engineering-ui` (`{{ROOT_PACKAGE}}.engineering.ui`) | `EngineeringFeatureImpl`, menu screen + ViewModel + pure search filter, built-in "Device" section, `DeviceInfoProvider`, `EngineeringModule` (`@Multibinds` sections) |
| `engineering-ui/src/withInput`, `src/testWithInput` | the "Hardware input" section and its test — compiled only when `:input` exists (ENG-09) |
| `.claude/skills/application-architecture/references/engineering-menu.md` | rules, entry wiring, contributing a section |

Marker edits: two `include(...)` lines, `implementation(project(":engineering-ui"))` and
`implementation(project(":engineering-ui-api"))` (for the entry seam) in `:app`, the registration
`{{ROOT_PACKAGE}}.engineering.ui.EngineeringFeatureImpl().addToRegistry()` in `App.kt`, skill index line, `CLAUDE.md`
`components` + `modules`.

## Prerequisites

- `architecture` installed.
- Optional: `hardware-input` (before or after) — its section appears automatically.

## Questions (all skippable)

| Ask | Default |
|---|---|
| "Where should engineers open the menu? (a hidden gesture on a screen such as About, a debug-only app-menu option, or leave it unwired for now)" | unwired — the route exists, nothing opens it yet; say so in the report |

## Install

```
R plan engineering-menu
R install engineering-menu
```

Then, if the user chose an entry, wire it (manual, ENG-07; the reference's section 3 has the code):
1. add a nullable `engineeringRoute: String? = null` parameter to the hosting screen's `FeatureImpl` and pass a lambda
   to the screen;
2. pass `EngineeringRoutes.MENU` in that feature's registration line in `App.kt` (`:app` already depends on
   `:engineering-ui-api`);
3. call it from a `TapSequenceDetector` gesture or a debug-only `SetAppMenuOptions` item.

## Gate

```
./gradlew assembleDebug
./gradlew :engineering-ui-api:testDebugUnitTest :engineering-ui:testDebugUnitTest
```

## Completing deferred items

Nothing is tracked by the renderer. If the entry was left unwired, do the three wiring steps above whenever the
hosting screen exists, then run the gate and check on a device that the gesture opens the menu and back returns.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `MissingBinding: Set<EngineeringSection>` | `EngineeringModule` lost its `@Multibinds` — restore it |
| The "Hardware input" section is missing although `hardware-input` is installed | `settings.gradle.kts` lacks `include(":input")`, or the `findProject(":input")` block was edited out of `engineering-ui/build.gradle.kts` |
| A contributed section never appears | its `rows` flow never emits (it is shown after the first emission), or its Hilt module is in a source dir KSP does not see (ENG-09: use `sourceSets…kotlin`, not the variant API) |
| Unresolved `EngineeringRoutes` in `App.kt` | the `:engineering-ui-api` line was removed from `app/build.gradle.kts` — restore it |
| Toggle does nothing | the contributor's callback does not persist / re-emit; rows must reflect the new state |

## Commit message

`chore(arch-spec): engineering-menu [spec vX.Y.Z]`
