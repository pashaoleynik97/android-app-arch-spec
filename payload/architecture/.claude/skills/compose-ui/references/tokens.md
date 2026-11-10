# Tokens — colors, typography, dimens, theme mode (component: architecture)

Spec: `.arch-spec/spec/specs/55-design-system.md` §1–2 and §5. Golden rules in `SKILL.md`: DS-03 (read through
the theme accessor), DS-06 (no hardcoded values), DS-11 (previews). Files, all in
`lib-android/src/main/java/{{ROOT_PACKAGE_PATH}}/libandroid/ui/theme/`:

| File | Holds |
|---|---|
| `Color.kt` | private Palette (raw values) · {{THEME_PREFIX}}Colors (semantic roles) · light / dark builders · `toMaterialColorScheme()` |
| `Type.kt` | {{THEME_PREFIX}}FontFamily · {{THEME_PREFIX}}Typography (named styles) · `toMaterialTypography()` |
| `Dimens.kt` | {{THEME_PREFIX}}Dimens (spacing scale, semantic aliases, radius scale, component metrics) |
| `Theme.kt` | the {{THEME_PREFIX}}Theme composable + the same-named accessor object · {{THEME_PREFIX}}ThemeMode |

Contents: 1. Architecture · 2. Colors · 3. Typography · 4. Dimens · 5. Theme mode · 6. Adding a token ·
7. When the design arrives

---

## 1. Architecture

**DS-01** — The theme is three immutable holders in `:lib-android` — {{THEME_PREFIX}}Colors,
{{THEME_PREFIX}}Typography, {{THEME_PREFIX}}Dimens — each `@Immutable`. *Why:* every UI module depends on
`:lib-android`, so every feature gets the theme with no wiring; immutable holders let Compose skip
recomposition.
<!-- origin: arch-spec DS-01 -->

**DS-02** — Each holder is provided through a `staticCompositionLocalOf` with **no default** — the factory
throws "Wrap content in {{THEME_PREFIX}}Theme". *Why:* a default silently renders a second, wrong palette
(typically in a preview or a forgotten root); throwing makes the mistake impossible to miss.
<!-- origin: arch-spec DS-02 -->

**DS-04** — The theme composable also provides a real MaterialTheme built from the same tokens, so stock
Material 3 components follow the palette. Roles with no Material slot are read from the accessor directly.
*Why:* stock components used anywhere pick up the same palette instead of Material's defaults.
<!-- origin: arch-spec DS-04 -->

## 2. Colors

**DS-07** — Colors are semantic, named by role, never by hue. The private Palette holds raw values;
{{THEME_PREFIX}}Colors exposes roles: Material slots (`primary`, `onPrimary`, `primaryContainer`, `background`,
`surface`, `onSurface`, `surfaceVariant`, `outline`, `error` …) plus roles Material lacks — `success`,
`warning`, `info`, `neutral` with `on*` and soft `*Container` variants, `textSecondary`, `textTertiary`,
`outlineStrong`, input roles (`inputFill`, `inputBorder`, `inputBorderFocus`, `errorBorder`, `errorText`),
`scrim`. *Why:* a role survives a rebrand; a hue name in a call site does not.
<!-- origin: arch-spec DS-07 -->

**DS-08** — Two roles with the same value today are still two tokens; imperceptibly different design values
map to the nearest existing role instead of a new token. *Why:* names say what is meant, so either can move
independently; near-duplicates are palette noise.
<!-- origin: arch-spec DS-08 -->

```kotlin
Text(title, color = {{THEME_PREFIX}}Theme.colors.onSurface)
Box(Modifier.background({{THEME_PREFIX}}Theme.colors.warningContainer))
```

## 3. Typography

**DS-09** — One font family ({{THEME_PREFIX}}FontFamily — the system font until the design supplies one).
The scale mirrors the design's named text styles (`displayLarge` … `labelSmall`); a new style maps to a design
style rather than inventing a size; text styles carry no color. *Why:* matching names keep design and code
reviewable side by side; color in a style fights the ambient content color.
<!-- origin: arch-spec DS-09 -->

## 4. Dimens

**DS-10** — {{THEME_PREFIX}}Dimens holds a spacing scale (`spaceTiny` … `spaceXLarge`), semantic aliases
(`screenEdge`, `itemSpacing`, `contentSpacing`), one radius scale (`radiusSmall` … `radiusXLarge`) and component
metrics (`borderWidth`, `buttonHeight`, `inputHeight`, `appBarHeight`, `iconSize`, `touchTarget`). Shapes are
built from radius tokens (`RoundedCornerShape(dimens.radiusMedium)`). *Why:* one scale per concept; semantic
aliases let a screen say "gutter" instead of a number.
<!-- origin: arch-spec DS-10 -->

## 5. Theme mode

**DS-05** — Theme mode is the install answer `THEME_MODE`, recorded in {{THEME_PREFIX}}ThemeMode. Default
`light-only`: the theme never consults the system dark setting, no dynamic (wallpaper) color, no
`values-night` resources, a light framework parent theme. With `light-dark` a second Colors instance exists
under the **same token names**; dynamic color still needs its own recorded decision. *Why:* a dark theme
doubles every visual review; making it a parameter keeps the choice explicit (ADR-0007 in the spec).
<!-- origin: arch-spec DS-05 -->

## 6. Adding a token

**DS-26** — To add a token: add the raw value to Palette (or the scale), expose it as a semantic field on the
holder (both light and dark builders when `light-dark`), wire it into the Material mapping if a slot exists,
and update this reference in the same change. *Why:* a token missing from the skill is a token the next agent
re-invents under another name.
<!-- origin: arch-spec DS-26 -->

## 7. When the design arrives

**DS-24** — Missing design inputs are first-class: without a palette the install renders neutral placeholder
colors (`COLOR_PRIMARY`, `COLOR_ERROR`, `COLOR_SUCCESS`, `COLOR_WARNING`), the system font, the default
reference device and `light-only`, and records each as deferred in `.arch-spec/state.json`. The app builds
and runs with them. *Why:* architecture work does not wait for a design file; placeholders that compile let
screens be built against stable token names.
<!-- origin: arch-spec DS-24 -->

**DS-25** — Completing a design input changes only token **values** — never names or call sites; new roles
the design introduces become tokens, never literals. *Why:* the payoff of semantic tokens — a rebrand is a
one-file change.
<!-- origin: arch-spec DS-25 -->

Procedure (also in `.arch-spec/spec/INSTALL.md`, "Complete deferred items"):

1. Palette: `python3 .arch-spec/spec/installer/render.py set --target . COLOR_PRIMARY=#RRGGBB …`, then
   `python3 .arch-spec/spec/installer/render.py rerender --target . COLOR_PRIMARY COLOR_ERROR COLOR_SUCCESS COLOR_WARNING`.
   An edited `Color.kt` is not overwritten — the new rendering lands next to it as `Color.kt.arch-spec-new`;
   merge the four brand values by hand. Map any further design colors to roles (DS-07, DS-08).
2. Font: drop the static font faces and their licence into the res/font folder of `:lib-android`, then
   point {{THEME_PREFIX}}FontFamily at them as its KDoc in `Type.kt` describes; set `FONT_FAMILY`.
3. Reference device: set `REFERENCE_DEVICE_DP=<w>x<h>` and rerender it — the preview annotations follow.
4. Theme mode: set `THEME_MODE` and rerender; with `light-dark` fill the dark builder in `Color.kt`.
5. Compare previews of the reference screens before and after; run `./gradlew assembleDebug testDebugUnitTest`.
