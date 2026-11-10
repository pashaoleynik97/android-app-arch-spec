---
name: compose-ui
description: >
  UI and Jetpack Compose rules for this project — the {{THEME_PREFIX}}Theme design system (semantic color,
  typography and dimension tokens read through {{THEME_PREFIX}}Theme.colors / typography / dimens), the base UI
  kit (button, button row, text field, top banner, bottom sheet, dialog, blocking message screen), host chrome
  driven by screens (app bar, banners, app menu), screen structure, previews at the reference device, strings,
  icons and accessibility. Use this whenever building or styling Compose UI, adding a screen or component,
  choosing colors, fonts or spacing, writing a @Preview, or when the design changes. For modules, DI, Gradle
  and ViewModel rules use the application-architecture skill.
---

# Compose UI

How screens look and how they talk to the host chrome. The design system lives in `lib-android`
(package `{{ROOT_PACKAGE}}.libandroid.ui`): tokens under `theme`, the kit under `compose.uikit`, host chrome
under `compose.statusbar`, preview annotations under `preview`. Rules carry spec IDs
(`.arch-spec/spec/specs/`, the design-system and presentation files) or project `PRJ-` IDs.

## How to use this skill

1. Read the golden rules below before writing any composable.
2. Open the reference for the job: tokens (adding or changing colors / type / spacing, design arriving),
   kit (using or adding a component), host chrome (app bar, banners, menu), screen layout (structure,
   previews, insets, strings).
3. Copy the shape of the reference screen in the `home-ui` module before inventing one.

## Golden rules

**DS-03** — Read every style value through the theme accessor —
`{{THEME_PREFIX}}Theme.colors.primary`, `{{THEME_PREFIX}}Theme.typography.titleMedium`,
`{{THEME_PREFIX}}Theme.dimens.screenEdge` — which reads CompositionLocals provided by the
`{{THEME_PREFIX}}Theme { }` composable; never a mutable global theme object. *Why:* CompositionLocal reads are
recomposition-aware; a global singleton is not, so changes to it redraw nothing.
<!-- origin: arch-spec DS-03 -->

**DS-06** — No hardcoded `Color(0x…)`, `.sp` or magic `.dp` in UI code. A missing value is added to the token
holder first; geometry used once inside one component may be a named `private val` in that file; branded
one-off surfaces are the only other exception (DS-19). *Why:* hardcoded values are invisible to a palette
change and to review.
<!-- origin: arch-spec DS-06 -->

**DS-13** — Use the kit component for its role before writing ad-hoc Compose: one parametrized button, the
equal-height button row, one stateless text field, the top banner, the bottom sheet, the dialog, the blocking
message screen. Feature-specific composables stay in the feature's `components` package and move to the kit
only when a second feature needs them. *Why:* a small kit that is actually used beats a large speculative
one; one component per role prevents five slightly different buttons.
<!-- origin: arch-spec DS-13 -->

**DS-20** — Every user-visible string is a string resource in the module that uses it, keys prefixed by
feature (`orders_title`); translations live in `values-<locale>/strings.xml`; ViewModels never resolve
strings. *Why:* hardcoded text cannot be translated, and per-module strings keep features movable.
<!-- origin: arch-spec DS-20 -->

**UI-11** — A destination is a public stateful `XScreen(navigation lambdas…, modifier, viewModel =
hiltViewModel())` that collects `uiState` with lifecycle, wires `uiCallbacks` in a `DisposableEffect`, and
calls a private stateless body that takes the Ux state and lambdas only — never the ViewModel. Lambdas
captured by effects go through `rememberUpdatedState`. *Why:* the stateless body is previewable and
recomposes predictably; passing the ViewModel down hides which intents a component can fire.
<!-- origin: arch-spec UI-11 -->

**UI-24** — Screens declare host chrome only through the `Set…` effects (app bar, banner, menu options),
which apply only while the destination is RESUMED; a screen never draws its own app bar or banner host.
*Why:* during a transition both destinations are composed; without the RESUMED gate the leaving screen
overwrites the arriving screen's chrome.
<!-- origin: arch-spec UI-24 -->

**DS-11** — Previews render the stateless body from literal Ux states — one per meaningful state (content,
empty, failure, loading) — wrapped in `{{THEME_PREFIX}}Theme { }` and sized to the reference device through
the shared preview annotations. *Why:* previews at a large default size hide exactly the overflow and
wrapping the smallest supported screen shows.
<!-- origin: arch-spec DS-11 -->

## Design inputs

The palette, font, reference device and theme mode may have been skipped at install time (no design yet).
Placeholders are used until then and listed as deferred in `.arch-spec/state.json`. When the design arrives,
only token **values** change — never token names or call sites; follow the tokens reference, section
"When the design arrives".

## References

<!-- <arch-spec:references> -->
<!-- </arch-spec:references> -->

## Changing this skill

A new token, kit component or chrome control updates this skill in the same change. Rules change only
through the **skills-maintenance** skill (amend / capture), with a `CHANGELOG.md` entry next to this file.
