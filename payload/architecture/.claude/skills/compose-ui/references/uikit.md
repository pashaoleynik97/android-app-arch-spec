# UI kit — the base components and their rules (component: architecture)

Spec: `.arch-spec/spec/specs/55-design-system.md` §3–4. Golden rule in `SKILL.md`: DS-13 (use the kit
component for its role). Package `{{ROOT_PACKAGE}}.libandroid.ui.compose.uikit` in `:lib-android`; each
component has previews in its file.

Contents: 1. Inventory · 2. Button and button row · 3. Text field · 4. Banner data · 5. Sheets and dialogs ·
6. Icons, accessibility · 7. Adding a component

---

## 1. Inventory

| Component | Role |
|---|---|
| {{THEME_PREFIX}}Button (+ {{THEME_PREFIX}}ButtonVariant, {{THEME_PREFIX}}ButtonSize, {{THEME_PREFIX}}ButtonContentLayout) | the one parametrized button |
| {{THEME_PREFIX}}ButtonRow | buttons side by side, equal height |
| {{THEME_PREFIX}}TextField | the one stateless text input |
| TopSnackBar / TopSnackbarHost / TopSnackbarData / TopSnackbarStyle | the banner, its host (host-chrome reference) and its data |
| {{THEME_PREFIX}}BottomSheet | modal sheet shown while a state flag is set |
| {{THEME_PREFIX}}Dialog | confirmation dialog driven by state; actions are kit buttons |
| {{THEME_PREFIX}}BlockingMessageScreen (+ BlockingAction) | full-screen dead end / permission / failure body with actions |
| {{THEME_PREFIX}}Loader, {{THEME_PREFIX}}LoadingFrame | progress indicator; full-body loading state |
| {{THEME_PREFIX}}Icons | project-local vector glyphs used by the kit |

## 2. Button and button row

**DS-14** — {{THEME_PREFIX}}Button is one component with `variant` (Primary, Secondary, Negative, Outline),
`size`, optional `icon` + `contentLayout`, `loading` and `enabled`; colors come from tokens per variant. Its
height is a token **minimum** (`heightIn`), never fixed; **width is the caller's** (`Modifier.fillMaxWidth()`
or `Modifier.weight(1f)` in a row). Labels are word-safe: a multi-word label wraps between words onto a second
line within the minimum height and shrinks in small steps down to a floor size rather than splitting a word; a
one-word label is single-line and ellipsizes past the floor. `loading` swaps the label for a loader and
disables input. *Why:* translations and small screens produce long labels; a button that grows or splits a
word is the most visible layout bug on small devices.
<!-- origin: arch-spec DS-14 -->

**DS-15** — Buttons side by side go in {{THEME_PREFIX}}ButtonRow (intrinsic-min height; each child
`Modifier.weight(1f).fillMaxHeight()`); every row of buttons uses it. *Why:* when one label wraps, its
neighbours stay the same height.
<!-- origin: arch-spec DS-15 -->

```kotlin
{{THEME_PREFIX}}ButtonRow {
    {{THEME_PREFIX}}Button(stringResource(R.string.orders_cancel), onClick = onCancel,
        variant = {{THEME_PREFIX}}ButtonVariant.Outline, modifier = Modifier.weight(1f).fillMaxHeight())
    {{THEME_PREFIX}}Button(stringResource(R.string.orders_confirm), onClick = onConfirm,
        loading = ux.isSubmitting, modifier = Modifier.weight(1f).fillMaxHeight())
}
```

## 3. Text field

**DS-16** — {{THEME_PREFIX}}TextField is stateless: the caller owns `value`, `isError`, `enabled`; `errorText`
shows only when `isError`; a trailing icon has a content description and is clickable only when
`onTrailingIconClick` is given. *Why:* state held inside an input fights the ViewModel's single source of
truth (UI-04).
<!-- origin: arch-spec DS-16 -->

## 4. Banner data

**DS-17** — TopSnackbarData is the single source of truth for a banner, and its UX rules live on the data
class, pinned by TopSnackbarDataTest: a banner with a close (X) never auto-dismisses; when X is the only
action, a tap anywhere on the banner closes it; with a retry action only X closes; `durationMillis = null`
means no timeout. A screen raises at most one banner, only through `SetAppSnackbar` (host-chrome reference).
*Why:* a rule a call site can forget gets forgotten; on the model it applies to every banner, including ones
written later.
<!-- origin: arch-spec DS-17 -->

```kotlin
@Composable
private fun OrdersNotice.toSnackbarData() = when (this) {
    OrdersNotice.RefreshFailed -> TopSnackbarData(
        title = stringResource(R.string.orders_refresh_failed),
        style = TopSnackbarStyle.Error, showRetry = true, showClose = true,
    )
}
```

## 5. Sheets and dialogs

**DS-18** — Sheets and dialogs are state-driven: shown while a State flag or sealed value is set, dismissed by
an intent that clears it; they never own business state. *Why:* a dialog living only in composition state is
lost on rotation and invisible to tests.
<!-- origin: arch-spec DS-18 -->

## 6. Icons and accessibility

**DS-22** — Icons and illustrations are vector drawables converted from SVG (paths only; filters and shadows
dropped), loaded with `painterResource` / `ImageVector.vectorResource`; raster-only art goes in
`res/drawable` as-is. Kit glyphs are project-local ({{THEME_PREFIX}}Icons); the Material icons artifact is
not a dependency. A bundled font ships its licence file. *Why:* vectors scale to any density at a fraction of
the size; the extended icon set is large and rarely matches the design.
<!-- origin: arch-spec DS-22 -->

**DS-23** — Interactive icons have a `contentDescription`, decorative ones pass `null`; touch targets meet the
platform minimum (`touchTarget` dimen); color is never the only carrier of meaning (status also has an icon or
text). *Why:* cheap at design-system level, expensive to retrofit per screen.
<!-- origin: arch-spec DS-23 -->

## 7. Adding a component

A component moves into the kit only when a second feature needs it; until then it lives in the feature's
`components` package. A kit component takes data and lambdas (no ViewModel), reads only tokens, has previews
per state at the reference width, a JVM test for any decision it makes (put the decision on its data class,
UI-31), and a row in the inventory above — added in the same change (skills-maintenance, capture).
