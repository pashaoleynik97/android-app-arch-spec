# Recommended: engineering-menu — hidden diagnostics menu (ENG)

Tier: **Recommended** (component `engineering-menu`). SHOULD / MAY guidance with a *why* per rule.

The component adds a UI-only feature (no domain module): `:engineering-ui-api` (`{{ROOT_PACKAGE}}.engineering.ui.api`
— `EngineeringRoutes.MENU`, the `EngineeringSection` row contract, `TapSequenceDetector`) and `:engineering-ui`
(`{{ROOT_PACKAGE}}.engineering.ui` — the searchable menu screen, its ViewModel, the built-in "Device" section,
`DeviceInfoProvider`). It is registered in `App` through the installer's `feature-registrations` marker. When the
`hardware-input` component is installed, a "Hardware input" section is compiled in automatically.

## When to use

- Field devices or testers need to see **what is running** (device, OS, app version) and flip **device-level
  diagnostics** (driver choice, verbose logging) without a debug build or a cable.
- Several infra modules have knobs or state worth exposing to engineers, and you want one place for them.

## When not to

- Consumer apps with no support or field-engineering process: the hidden entry is attack surface with no user.
- Settings meant for end users: those belong in a normal, discoverable settings feature.
- Developer-only tools needed only in debug builds: a debug-only module is simpler (MOD-17).

---

## 1. Shape

**ENG-01** — The menu SHOULD be a normal feature trio without a domain module (`:engineering-ui-api` +
`:engineering-ui`), registered like any feature, and SHOULD ship in **release** builds too; a row whose setting the
build cannot honour SHOULD be shown **disabled** with the reason in its description, not hidden.
*Why:* field diagnostics are needed exactly on production builds; a hidden row reads as "this build has no such
setting", a disabled one says why.

**ENG-02** — Menu content SHOULD be **data contributed by the module that owns it**: each module binds
`@IntoSet EngineeringSection` (key, title, order, `rows: Flow<List<EngineeringRow>>`), and the menu declares the set
with `@Multibinds`. A row carries a control: `None`, `Navigate(route)`, `Toggle(checked, onCheckedChange)`,
`Picker(options, selectedKey, onSelect)`, and a real `enabled` flag.
*Why:* the menu knows no infra module, so installing or removing a component adds or removes its rows with no edit
(MOD-03); rows as data are searchable and testable without a device.

**ENG-03** — Controls SHOULD act on the **live row**, looked up by key at tap time, and a disabled row SHOULD ignore
taps and switch moves.
*Why:* rows are re-emitted as state changes; a callback captured from an old emission would act on stale state.

**ENG-04** — Section rows SHOULD be collected only while the menu is visible (`WhileSubscribed`), and a section that
has not emitted yet SHOULD NOT hold back the others.
*Why:* live rows (driver state, last reading) cost nothing while the menu sits in the back stack; one slow
contributor must not blank the whole menu.

## 2. Search and copy

**ENG-05** — The search SHOULD be a **pure function over the rendered strings** (title or description contains the
trimmed query, ignoring case; sections left empty are dropped; blank query → unchanged), unit-tested.
*Why:* it must match exactly what the engineer reads, runtime values included; an empty header over nothing reads as
a failed load.

**ENG-06** — Copy SHOULD be English-only plain strings (no translations) for rows; values that could be secrets
(readings, tokens) SHOULD be masked even here.
*Why:* engineer-facing text changes with the diagnostics, not with the product's locales; a screenshot of the menu
travels into tickets (KERNEL-05).

## 3. Entry

**ENG-07** — The menu SHOULD have **no ordinary navigation entry**. A screen that offers one receives
`EngineeringRoutes.MENU` from the host as a nullable `FeatureImpl` seam (MOD-18) and opens it with a plain
`navigate` from a hidden gesture (`TapSequenceDetector`, e.g. 7 taps on the version line of an "About" row) or, on
debug builds, an app-menu option (`SetAppMenuOptions`). The entry SHOULD NOT be a magic credential typed into a
sign-in form.
*Why:* a null seam keeps the screen buildable without the menu; a credential path couples sign-in to diagnostics,
risks the "credential" being sent to the server when the interception is missed, and is easily leaked.

**ENG-08** — The device section SHOULD read identity through a `DeviceInfoProvider` seam, with the app version taken
from `PackageManager`.
*Why:* the ViewModel stays a JVM unit; a library cannot see `:app`'s `BuildConfig`, and the installed package is the
honest answer (suffixes included).

## 4. Optional integrations

**ENG-09** — Integration between two optional components SHOULD be compiled **conditionally on the project's
module list**: the consuming module's build file checks `rootProject.findProject(":other") != null` and, when true,
adds a dedicated source directory to the `main` (and `test`) **Kotlin** source sets through the typed
`LibraryExtension` plus `implementation(project(":other"))`. The glue lives on the side whose dependency direction is
legal (UI → infra, not infra → UI).
*Why:* every component must build alone and in any install order with no hand edit; the module list is part of the
configuration, so this is configuration-cache safe. Kotlin source sets (not the variant API's static source dirs) are
required because KSP must see the glue's Hilt modules — otherwise an `@IntoSet` is silently missing.

This component uses it for hardware input (`engineering-ui/src/withInput`, `src/testWithInput`). The `network`
component's HTTP log settings (`HttpLogSettingsRepository`: full bodies toggle + size limit, `isAvailable` =
debug build) can be contributed the same way later: a `src/withNetwork` directory in `:engineering-ui` with an
`EngineeringSection` whose toggle/picker call the repository, guarded by `findProject(":network")`.

## 5. Testing

**ENG-10** — The ViewModel SHOULD be tested with fake sections whose rows the test drives (order, filtering,
toggle/picker/navigate dispatch, disabled rows, picker following live state) and a recording `UiCallbacks`; a
contributed section SHOULD be tested against its real collaborators' fakes.
*Why:* the menu is pure data flow; everything except drawing is JVM-testable.

## Adapting this to your app

- **Wire an entry** (ENG-07): add `engineeringRoute: String? = null` to the `FeatureImpl` of the screen that hosts the
  gesture (an "About" or settings screen), pass `EngineeringRoutes.MENU` in `App`'s registration, and call
  `navController.navigate(route)` from the gesture.
- **Add a section** for any module: implement `EngineeringSection`, bind it `@IntoSet` in that module (if it may
  depend on `:engineering-ui-api`) or in `:engineering-ui` behind ENG-09.
- **Release policy:** decide which rows may act in release; disable the rest with a reason (ENG-01).
- **Example-only:** the Device section's rows and the route string.
