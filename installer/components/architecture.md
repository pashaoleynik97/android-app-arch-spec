# Component: architecture (Core)

Rules: `specs/10-modules.md` (MOD), `specs/30-di.md` (DI), `specs/40-kernel.md` (KERNEL), `specs/50-presentation.md`
(UI), `specs/55-design-system.md` (DS), `specs/70-testing.md` (TEST). Shorthands as in `INSTALL.md`:
`SPEC=.arch-spec/spec`, `R` = `python3 $SPEC/installer/render.py --target .`

## What it adds

| Module / file | Package | Contents |
|---|---|---|
| `:lib-common` | `{{ROOT_PACKAGE}}.libcommon` | `Reporter` + `logTag()`, `Scopes`, `AppForegroundWatch`, `DispatcherProvider`, `TimeSource` (bound once in `KernelModule`). No Compose, no UI. |
| `:feature-api` | `{{ROOT_PACKAGE}}.featureapi` | `FeatureApi.registerGraph(navGraphBuilder, navController)` + `addToRegistry()`, `FeaturesHolder` registry |
| `:lib-android` | `{{ROOT_PACKAGE}}.libandroid` | `{{THEME_PREFIX}}Theme` (Colors/Typography/Dimens holders, no-default locals, Material mirroring, `{{THEME_PREFIX}}ThemeMode`), host chrome (`AppStatusBarController`/`SetAppStatusBar`, `AppSnackbarController`/`SetAppSnackbar`, `AppMenuController`/`SetAppMenuOptions`, `TopSnackbarData`/`TopSnackbarHost`), base kit (`{{THEME_PREFIX}}Button`, `…ButtonRow`, `…TextField`, `…Dialog`, `…BottomSheet`, `…BlockingMessageScreen`, `…Loader`/`…LoadingFrame`, `…Icons`), `BaseViewModel`, `ViewModelState` + `upd {}`, `AppTransitions`, `NavResultKey` helpers, `@ReferenceDevicePreview` |
| `:core-testing` | `{{ROOT_PACKAGE}}.coretesting` | `MainDispatcherRule`, `TestDispatcherProvider`, `FakeTimeSource`, `RecordingReporter` — `testImplementation` only |
| `:app` | `{{APP_PACKAGE}}` | `App` (`@HiltAndroidApp`, eager singletons, `// <arch-spec:feature-registrations>` marker), `MainActivity` (edge-to-edge per theme mode), `AppNavHost` (single `NavHost` iterating `FeaturesHolder`, host chrome outside it, start destination `HomeRoutes.MAIN`), manifest, `strings.xml`, `themes.xml` |
| `:home-ui-api` / `:home-ui` / `:home` | `{{ROOT_PACKAGE}}.home.ui.api` / `.ui` / `.domain` | the reference feature trio, rendered from `feature-template/` with `FEATURE=home`: routes, `FeatureImpl`, Screen → Content split with reference-device previews, ViewModel contract (`UxState` / private `State` / `uiCallbacks` / intents), interactor + default impl + Hilt module, VM and interactor unit tests |

Replaced stock files: `AndroidManifest.xml`, `MainActivity.kt`, `res/values/strings.xml`, `res/values/themes.xml`.
Removed: `ui/theme/*.kt`, `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`. Marker edits: module includes,
`:app` dependencies on `:lib-common` / `:lib-android` / `:feature-api` / `:home-ui-api` / `:home-ui`, the home
registration line in `App.kt`, and (once `ai-layer` is installed) the module list in `CLAUDE.md`.

## Prerequisites

- `foundation` installed and its gate green.
- `ai-layer` is normally installed before this component; it is not required — the `CLAUDE.md` lines are added
  whenever `ai-layer` arrives.

## Questions (all skippable)

| Parameter | Ask | Safe placeholder |
|---|---|---|
| `APP_NAME` | "App name shown under the launcher icon?" | stock `app_name`, else `My App` |
| `THEME_PREFIX` | "Prefix for the design system (`<Prefix>Theme`, `<Prefix>Button`)?" | `APP_NAME` in PascalCase |
| `BASE_LOCALE` | "Language of the default strings?" | `en` |
| `EXTRA_LOCALES` | "Other languages to ship?" | none (deferred `locales.extra`) |
| `COLOR_PRIMARY`, `COLOR_ERROR`, `COLOR_SUCCESS`, `COLOR_WARNING` | "Brand / status colours (`#RRGGBB`), or skip until the design exists" | neutral placeholders (deferred `design.palette`) |
| `FONT_FAMILY` | "Font family, or `system`?" | `system` (deferred `design.typography`) |
| `REFERENCE_DEVICE_DP` | "Smallest screen the design targets, `WIDTHxHEIGHT` in dp?" | `360x640` (deferred `design.referenceDevice`) |
| `THEME_MODE` | "`light-only` or `light-dark`?" | `light-only` (deferred `design.themeMode`) |
| `FIGMA_URL` | "Link to the design, if any?" | empty (deferred `design.figma`) |

`APP_NAME` is written into `strings.xml`: avoid a raw `'` or `&` (escape as `\'` / `&amp;` afterwards if needed).

## Install

```
R plan architecture
R install architecture
```

The renderer also scaffolds the `home` feature trio and registers it. No manual edits are required.

Manual checks after install:
- The stock sources (`MainActivity.kt`, `ui/theme/`, the two `Example*Test.kt`, `res/values/colors.xml`) are
  removed from the stock namespace recorded by `R init`, even when `APP_PACKAGE` changed, and emptied directories
  are pruned. If any stock file is still there (e.g. the project was not stock), show it to the user and delete it
  only with consent.
- `EXTRA_LOCALES` answered: create `values-<locale>/strings.xml` in `:app` and in every `:x-ui` module that has
  strings, with the same keys (DS-20). This is documentation-only; nothing is rendered for it.

## Gate

```
./gradlew assembleDebug checkModuleGraph
./gradlew testDebugUnitTest
./gradlew spotlessCheck :app:lintDebug
```

Expected: all modules build; unit tests in `:lib-common`, `:lib-android`, `:feature-api`, `:core-testing`, `:home`,
`:home-ui` pass. Report the counts from `bash $SPEC/installer/gates/test-summary.sh .`.

Scaffolding further features is covered by the spec's own dry runs and by the `add-feature` skill. Don't
scaffold a probe feature during the install.

## Completing deferred items ("the design is ready")

1. **Palette** (`design.palette`): `R set COLOR_PRIMARY=#…` (and the status colours), then
   `R rerender COLOR_PRIMARY COLOR_ERROR COLOR_SUCCESS COLOR_WARNING`. Only `lib-android/…/ui/theme/Color.kt` is
   re-rendered (if unmodified); containers and on-colours are derived from the four inputs. For a full design
   palette, edit `Palette` and the role mapping in `Color.kt` by hand — add roles, never hardcode colours in
   screens (DS-06/07).
2. **Typography** (`design.typography`): drop the font's static files into `lib-android/src/main/res/font/`
   (lowercase names, plus its licence file), replace `{{THEME_PREFIX}}FontFamily` in `Type.kt` with
   `FontFamily(Font(R.font.…, FontWeight.…), …)`, then map the design's named text styles onto the
   `{{THEME_PREFIX}}Typography` slots (add slots for styles Material lacks). `R set FONT_FAMILY=<name>` records it.
3. **Reference device** (`design.referenceDevice`): `R set REFERENCE_DEVICE_DP=360x800`,
   `R rerender REFERENCE_DEVICE_DP` — updates the `@ReferenceDevicePreview` / `@ReferenceWidthPreview` sizes.
4. **Theme mode** (`design.themeMode`): `R set THEME_MODE=light-dark`, `R rerender THEME_MODE` (re-renders
   `Theme.kt`). Then review `dark{{THEME_PREFIX}}Colors()` against the design, and consider a DayNight parent in
   `app/src/main/res/values/themes.xml` so the window behind the first frame matches the system mode.
5. **Figma** (`design.figma`): `R set FIGMA_URL=…` — recorded for the agent; nothing is rendered.

Gate again, then commit `chore(arch-spec): complete design`.

## Troubleshooting

| Symptom | Cause → fix |
|---|---|
| Hilt/KSP: "… could not be resolved" for many bindings, no file/line | a slash-star inside a KDoc opened a nested comment (BUILD-38); search the module for `/*` in comments. |
| KSP: `invalid type parameter: long` | an injected `@JvmInline value class` over a primitive; use a `data class` (BUILD-37). |
| `[Dagger/DuplicateBindings] DispatcherProvider` / `TimeSource` / `AppForegroundWatch` | a second module binds a kernel seam; only `:lib-common`'s `KernelModule` may. |
| "No `<Prefix>Colors` provided" at runtime or in a preview | content outside `{{THEME_PREFIX}}Theme { }` — wrap the preview. |
| "AppStatusBarController not provided" | a screen composed outside `AppNavHost` (e.g. a preview calling the stateful `XScreen`) — preview the stateless `XContent` instead. |
| `Method … in android.util.Log not mocked` | a module not using the conventions; apply `{{CONVENTION_PREFIX}}.android.library` (it sets `isReturnDefaultValues`). |
| `.arch-spec-new` files after install | the user edited a file the component ships; diff, merge, delete the `.arch-spec-new` (INSTALL rule 6). |
| App starts on a blank screen | the start destination's feature is not registered: check the `App.kt` registration block and `:home-ui` in `:app` dependencies. |

## Commit message

`chore(arch-spec): architecture [spec vX.Y.Z]`
