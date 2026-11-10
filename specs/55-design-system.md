# 55 — Design system: theme tokens, base kit, resources (DS)

Tier: **Core** (component `architecture`). `<Prefix>` is the `{{THEME_PREFIX}}` parameter (`<Prefix>Theme`,
`<Prefix>Button`, …). Design inputs — `COLOR_*`, `FONT_FAMILY`, `REFERENCE_DEVICE_DP`, `THEME_MODE`,
`FIGMA_URL` — are all deferrable (section 5).

Contents: 1. Token architecture · 2. Tokens · 3. Base kit · 4. Resources and system UI · 5. Deferred design inputs

---

## 1. Token architecture

```
lib-android/src/main/java/{{ROOT_PACKAGE_PATH}}/libandroid/ui/theme/
  Color.kt   private Palette + <Prefix>Colors + Local<Prefix>Colors + toMaterialColorScheme()
  Type.kt    font family + <Prefix>Typography + Local<Prefix>Typography + toMaterialTypography()
  Dimens.kt  <Prefix>Dimens + Local<Prefix>Dimens
  Theme.kt   @Composable <Prefix>Theme(content)  +  object <Prefix>Theme (accessor)
lib-android/src/main/java/{{ROOT_PACKAGE_PATH}}/libandroid/ui/compose/uikit/   the base kit
```

**DS-01** — The theme lives in `:lib-android` (`…libandroid.ui.theme`) as three **immutable** token holders:
`<Prefix>Colors`, `<Prefix>Typography`, `<Prefix>Dimens`, each annotated `@Immutable`.
*Why:* every `:x-ui` depends on `:lib-android`, so every feature gets the theme with no wiring; immutable
holders let Compose skip recomposition.

**DS-02** — Each holder is provided through a `staticCompositionLocalOf` **with no default**:
```kotlin
val Local<Prefix>Colors = staticCompositionLocalOf<<Prefix>Colors> {
    error("No <Prefix>Colors provided. Wrap content in <Prefix>Theme { }.")
}
```
*Why:* a default silently renders a second, wrong palette (typically in previews or a forgotten root); throwing
makes the mistake impossible to miss.

**DS-03** — `<Prefix>Theme` is **both** a `@Composable` provider and a same-named `object` accessor:
```kotlin
@Composable fun <Prefix>Theme(content: @Composable () -> Unit) { /* CompositionLocalProvider + MaterialTheme */ }
object <Prefix>Theme {
    val colors: <Prefix>Colors @Composable @ReadOnlyComposable get() = Local<Prefix>Colors.current
    val typography: <Prefix>Typography @Composable @ReadOnlyComposable get() = Local<Prefix>Typography.current
    val dimens: <Prefix>Dimens @Composable @ReadOnlyComposable get() = Local<Prefix>Dimens.current
}
// usage
Text(text, color = <Prefix>Theme.colors.primary, style = <Prefix>Theme.typography.titleMedium)
```
A mutable global theme object is forbidden.
*Why:* reads through CompositionLocals are recomposition-aware; a global mutable singleton is not, and changes
to it do not redraw anything.

**DS-04** — `<Prefix>Theme` also provides a real `MaterialTheme` built from the same tokens
(`colors.toMaterialColorScheme()`, `typography.toMaterialTypography()`). Extended roles with no Material slot are
read from `<Prefix>Theme` directly.
*Why:* stock Material 3 components used anywhere pick up the same palette instead of Material's defaults.

**DS-05** — Theme mode is a parameter (`THEME_MODE`), and the **default is a single light theme**
(`light-only`, ADR-0007):
- `<Prefix>Theme` takes no `darkTheme` parameter and never calls `isSystemInDarkTheme()`;
- no dynamic (wallpaper) color;
- no `values-night/`; the XML base theme has a light framework parent;
- the app renders identically regardless of the system dark-mode setting.

With `light-dark`, the token **names** stay identical and two `<Prefix>Colors` instances exist; dynamic color
still requires its own recorded decision.
*Why:* a dark theme doubles every visual review; shipping a half-done one is worse than none. Making it a
parameter keeps the choice explicit.

**DS-06** — UI code MUST NOT hardcode `Color(0x…)`, `.sp`, or magic `.dp` values. Every value comes from
`<Prefix>Theme.colors/typography/dimens` (or `MaterialTheme.*` for stock components); a missing value is added to
a holder first. Component-internal geometry used once may be a named `private val` in that component's file.
The only other exception is DS-19.
*Why:* hardcoded values are invisible to a palette change and to review; named private geometry keeps one-off
measurements honest without bloating the scale.

## 2. Tokens

**DS-07** — Colors are **semantic** and named by role, never by hue. A private `Palette` holds raw values;
`<Prefix>Colors` exposes roles: the Material slots (`primary`, `onPrimary`, `primaryContainer`, `error`,
`background`, `surface`, `onSurface`, `outline`, …) plus extended roles Material lacks: `success`, `warning`,
`info`, `neutral` with their `on*` and soft `*Container` tints, `textSecondary`, `textTertiary`, input roles
(`inputFill`, `inputBorder`, `inputBorderFocus`, `errorBorder`, `errorText`) and others the design needs.
*Why:* a role survives a rebrand; `blue500` in a call site does not.

**DS-08** — Two roles with the same value today are still two tokens (a screen gutter and an input padding that
happen to be equal). Conversely, imperceptibly different values in the design are **not** tokenised separately —
they map to the nearest existing role.
*Why:* names say what is meant, so either can move independently; near-duplicate tokens are palette noise.

**DS-09** — Typography: one font family (`FONT_FAMILY`; `system` until the design supplies one). The scale mirrors
the design's **named text styles**; a new style maps to a design style rather than inventing a size. Text styles
carry **no color**. Styles without a Material slot are read from `<Prefix>Theme.typography` directly.
*Why:* matching named styles keeps design and code reviewable side by side; color in a text style fights the
ambient content color.

**DS-10** — `<Prefix>Dimens` holds a **spacing scale** (base steps plus any off-scale steps the design genuinely
uses), **semantic aliases** (`screenEdge`, `itemSpacing`, `borderWidth`, `buttonHeight`, `inputHeight`,
`iconSize`, `appBarHeight`), and a single **radius scale** (`radiusSmall … radiusXLarge`). Shapes are built from
radius tokens (`RoundedCornerShape(dimens.radiusSmall)`). A future shapes system follows the same holder +
local + accessor shape.
*Why:* one scale per concept; semantic aliases let a screen say "gutter" instead of a number.

**DS-11** — Previews are wrapped in `<Prefix>Theme { }` and sized to the **reference device**
(`REFERENCE_DEVICE_DP`, the smallest screen the design targets): component previews set `widthDp`, full-screen
previews set `widthDp` and `heightDp`. A shared preview annotation in `:lib-android` carries those values.
*Why:* previews at a large default size hide exactly the overflow and wrapping the smallest device shows.

**DS-12** — Screen structure: the header (host app bar) is fixed, the body scrolls, and a footer of actions, when
present, is fixed. An action that belongs to the content flow (a form's submit) sits inside the scrolling body.
*Why:* a predictable frame makes small screens usable and keeps primary actions reachable.

## 3. Base kit

**DS-13** — Reusable, theme-driven components live in `:lib-android` `…ui.compose.uikit` and are preferred over
ad-hoc Compose. Core ships this base kit; anything else is added when a second screen needs it:

| Component | Role |
|---|---|
| `<Prefix>Button` + `<Prefix>ButtonRow` | the one parametrized button and the equal-height row |
| `<Prefix>TextField` | the one stateless text input (label, placeholder, error text, trailing icon) |
| `TopSnackBar` / `TopSnackbarHost` / `TopSnackbarData` | the banner and its host (host chrome, UI-26) |
| `<Prefix>BottomSheet` | modal sheet driven by a state flag |
| `<Prefix>Dialog` | confirmation dialog driven by state |
| `<Prefix>BlockingMessageScreen` | full-screen dead end / permission / failure body with actions |
| `<Prefix>Icons` | project-local `ImageVector`s used by the kit |

Feature-specific composables stay in the feature (`components/`); a component moves to the kit only when it is
generic.
*Why:* a small kit that is actually used beats a large speculative one; one parametrized component per role
prevents five slightly different buttons.

**DS-14** — `<Prefix>Button`:
- **one** component with `variant` (primary, secondary, negative, outline), `size`, optional `icon`, `loading`,
  and `enabled`/activated state; colors come from tokens per variant;
- height is a `heightIn` **minimum** from tokens, never a fixed height; **width is the caller's**
  (`Modifier.fillMaxWidth()` or `Modifier.weight(1f)` in a row);
- **word-safe labels**: a multi-word label wraps between words onto a second line that fits within the minimum
  height (padding sized for two lines); a word is never split — the label shrinks in small steps down to a floor
  size until every break falls on whitespace; a one-word label is `maxLines = 1` and ellipsizes past the floor;
- `loading` swaps content for a loader and disables input; with a start icon, the icon's width is reserved on both
  sides so the label stays optically centered.
*Why:* translations and small screens produce long labels; a button that grows or splits a word mid-way is the
most visible layout bug on small devices.

**DS-15** — Buttons side by side go in `<Prefix>ButtonRow` (`Row` with `height(IntrinsicSize.Min)`; each child
`Modifier.weight(1f).fillMaxHeight()`). Every row of buttons uses it.
*Why:* if one label wraps, its neighbours stay the same height.

**DS-16** — `<Prefix>TextField` is **stateless / unidirectional**: the caller owns `value`, `isError`, `enabled`;
`errorText` shows only when `isError`; a trailing icon has a content description and is clickable only when
`onTrailingIconClick` is given.
*Why:* state held inside an input fights the ViewModel's single source of truth (UI-04).

**DS-17** — `TopSnackbarData` is the single source of truth for a banner, and its UX rules live **on the data
class**, pinned by unit tests:
```kotlin
@Immutable data class TopSnackbarData(
    val title: String, val description: String? = null,
    val style: TopSnackbarStyle = TopSnackbarStyle.Info, val icon: ImageVector? = null,
    val showRetry: Boolean = false, val showClose: Boolean = false,
    val durationMillis: Long? = DEFAULT_DURATION,
) {
    val autoDismissMillis: Long? get() = durationMillis?.takeIf { !showClose }   // an X never times out
    val dismissesOnBodyTap: Boolean get() = showClose && !showRetry             // X is the only action
}
```
- a banner with a close (X) **never auto-dismisses** — the user closes it;
- when X is the **only** action, a tap anywhere on the banner closes it (no ripple); with a retry action, only
  X closes;
- `data != null` shows, `null` hides; the host keeps the last value for the exit animation;
- one slot per screen, **owned by the raiser** (UI-24); screens raise banners only through `SetAppSnackbar`.
*Why:* a rule a call site can forget is a rule that gets forgotten; on the model it applies to every banner,
including ones written later.

**DS-18** — Sheets and dialogs are **state-driven**: shown while a `State` flag/sealed value is set, dismissed by
an intent that clears it. They never own business state.
*Why:* a dialog that survives only in composition state is lost on rotation and invisible to tests.

**DS-19** — **Branded one-off surfaces** (a splash, a marketing-style empty state) MAY use colors and geometry
outside the token set, scoped as `private` values in that screen's file; such colors MUST NOT be added to
`<Prefix>Colors`. Typography is **not** exempt. Full-bleed branded screens draw under the system bars and pad
content with `systemBarsPadding()`.
*Why:* off-theme brand colors in the token set corrupt it for every other screen; keeping them local keeps the
exception visible.

## 4. Resources and system UI

**DS-20** — All user-visible text comes from string resources: base language in `values/strings.xml`
(`BASE_LOCALE`), translations in `values-<x>/strings.xml` (`EXTRA_LOCALES`). Strings live in the module that
uses them; keys are prefixed by feature (`orders_title`). ViewModels never resolve strings (UI-06).
*Why:* hardcoded text cannot be translated; per-module strings keep features movable.

**DS-21** — The Activity is edge-to-edge (`enableEdgeToEdge`). With `light-only`, it forces **dark** system-bar
glyphs: `enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(TRANSPARENT, TRANSPARENT))` (and the same for the
navigation bar when it is drawn over light content). With `light-dark`, `SystemBarStyle.auto(…)` follows the
theme.
*Why:* the default style picks glyph color from the **system** dark setting, which renders white glyphs on a
light-only app's white bar on some devices.

**DS-22** — Icons and illustrations are **vector drawables** converted from SVG (paths only; filters and shadows
dropped) loaded with `painterResource` / `ImageVector.vectorResource`; raster-only art goes in `res/drawable`
as-is. Kit glyphs are project-local; the Material icons artifact is not a dependency. A bundled font ships its
license file in the module.
*Why:* vectors scale to any density at a fraction of the size; the extended icons artifact is large and its style
rarely matches the design.

**DS-23** — Accessibility: interactive icons have a `contentDescription`; decorative ones pass `null`; touch
targets meet the platform minimum (button minimum heights are at least that); color is never the only carrier of
meaning (status also has an icon or text).
*Why:* these are cheap at design-system level and expensive to retrofit per screen.

## 5. Deferred design inputs

**DS-24** — Missing design inputs are **first-class**, not a blocker. Without a palette, the installer renders a
neutral placeholder palette (`COLOR_PRIMARY`, `COLOR_ERROR`, `COLOR_SUCCESS`, `COLOR_WARNING` safe values),
`FONT_FAMILY = system`, the default reference device and `light-only`, and records each as **deferred**. The app
builds and runs with them.
*Why:* architecture work should not wait for a design file; placeholders that compile let screens be built
against stable token names.

**DS-25** — Completing a deferred design input changes **only** the palette / typography / dimens values (the
installer re-renders the palette file when it is unmodified); token **names** and call sites do not change. When
the design arrives, new roles are added as tokens, never as literals at call sites.
*Why:* this is the payoff of semantic tokens — a rebrand is a one-file change.

**DS-26** — Adding a token: add the raw value to the private palette or scale, expose it as a semantic field on
the holder, wire it into the Material mapping if a slot exists, and update the compose-ui skill's token table in
the same change.
*Why:* a token missing from the skill is a token the next agent re-invents under another name.
