# Host chrome — app bar, banners, app menu, insets (component: architecture)

Chrome is drawn by the host (`AppNavHost` in `:app`) outside the NavHost and driven by screens. Spec:
`.arch-spec/spec/specs/50-presentation.md` §6. Golden rule in `SKILL.md`: UI-24 (`Set…` effects apply only
while RESUMED). Types live in `:lib-android`: `…ui.compose.statusbar` (app bar, menu, the RESUMED mechanism)
and `…ui.compose.uikit` (banner controller and host).

Contents: 1. Controllers and effects · 2. Banners · 3. Re-rooting · 4. Insets · 5. Adding a host-level control

---

## 1. Controllers and effects

| Chrome | Controller (CompositionLocal) | Screen-side effect |
|---|---|---|
| app bar | AppStatusBarController (LocalAppStatusBarController) | `SetAppStatusBar(AppStatusBarConfig(title, subtitle, onBack, actions))` |
| screen banner | AppSnackbarController (LocalAppSnackbarController) | `SetAppSnackbar(data, onClose, onDismiss, onRetry)` |
| app menu | AppMenuController (LocalAppMenuController) | `SetAppMenuOptions(listOf(AppMenuItem(title, onClick, icon)))` |

**UI-23** — Chrome outside the NavHost is controlled through controllers provided as CompositionLocals with
**no default** (fail fast); controller state has a private setter; screens use only the declarative `Set…`
effects, never a controller directly. *Why:* screens declare what they need and the host decides how to draw
it; a missing provider is a crash in development, not a silently missing bar in production.
<!-- origin: arch-spec UI-23 -->

```kotlin
// inside XScreen (stateful part)
SetAppStatusBar(AppStatusBarConfig(title = stringResource(R.string.orders_title), onBack = onBack))
SetAppSnackbar(
    data = ux.notice?.toSnackbarData(),
    onClose = viewModel::onNoticeDismissed,
    onDismiss = viewModel::onNoticeDismissed,
    onRetry = viewModel::onRetry,
)
```

The banner slot is **owned by the raiser**: `hide(request)` clears only if the slot still holds that same
request, and a screen's request is withdrawn when its data changes or it leaves composition (UI-24). The app
bar is not hidden on dispose — the arriving screen overwrites it, so nothing flickers.

## 2. Banners

**UI-26** — Notices are layered by nesting TopSnackbarHost outside the NavHost, **outermost = highest
priority** (e.g. session notices > connectivity banner > screen banner). Host-level notice enums map to
TopSnackbarData in private `@Composable` extensions in the host. Screens never host their own
TopSnackbarHost. *Why:* a screen-local host draws under the app bar instead of over it, and priority by
nesting needs no arbitration code.
<!-- origin: arch-spec UI-26 -->

A module that adds a host-level notice (for example a connectivity banner) contributes a HostExtension
(`@Binds @IntoSet`) whose `Wrap` places one more TopSnackbarHost around the content; `AppNavHost` nests every
extension outside the screen-banner host, lower `order` = outer = higher priority. Core code never names the
module, and a screen never hosts a banner (seam details: application-architecture, modules reference, MOD-03).

## 3. Re-rooting and host events

**UI-25** — When the host re-roots navigation (session ended → sign-in), it explicitly resets chrome that
outlives destinations (close the menu, clear stale options; `AppNavHost` already resets menu options on every
destination change). A host action that only makes sense in a live session is gated by a flag from the host
shell ViewModel, not by a screen's opt-in. *Why:* chrome survives re-rooting; options last written by a
popped screen go stale.
<!-- origin: arch-spec UI-25 -->

**UI-28** — The host shell ViewModel emits host navigation events (go to sign-in) on
`MutableSharedFlow(replay = 0, extraBufferCapacity = 1)`, collected in `LaunchedEffect(navController)`.
*Why:* unlike a screen's exit flow, a host event must fire only while the host is collecting — a replayed
"go to sign-in" after recomposition would re-navigate wrongly.
<!-- origin: arch-spec UI-28 -->

## 4. Insets

**UI-27** — The Activity is edge-to-edge; the host pads the NavHost from the navigation bar once
(`navigationBarsPadding()`) and consumes the status-bar inset while the app bar (which pads itself) is shown.
Screens do not pad for system bars again; full-bleed branded screens are the exception (DS-19). *Why:* insets
applied at two levels double the gap on some devices and vanish on others.
<!-- origin: arch-spec UI-27 -->

## 5. Adding a host-level control

1. A controller class (`@Stable`, state with private setters) and a `staticCompositionLocalOf` that throws when
   missing, in `:lib-android`.
2. A `Set…` effect built on the same RESUMED mechanism the existing effects use (`onResumed` in
   `ResumedEffects.kt`), withdrawing only its own request on dispose.
3. Provide the controller in `AppNavHost` and draw it there.
4. JVM tests for the controller's ownership rules (see AppSnackbarControllerTest).
5. Add a row to the table in section 1 (skills-maintenance, capture).
