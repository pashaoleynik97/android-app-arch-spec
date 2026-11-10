# Screen layout — structure, previews, branded surfaces, system bars (component: architecture)

Spec: `.arch-spec/spec/specs/55-design-system.md` and `.arch-spec/spec/specs/50-presentation.md` §2. Golden
rules in `SKILL.md`: UI-11 (stateful screen + stateless body), DS-11 (previews at the reference device), DS-20
(strings). The reference screen is `HomeScreen` in the `home` feature.

Contents: 1. Anatomy of a screen · 2. Structure · 3. Previews · 4. Branded surfaces · 5. System bars

---

## 1. Anatomy of a screen

```kotlin
@Composable
fun OrdersScreen(
    onOpenOrder: (id: String) -> Unit,                   // navigation lambdas first
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: OrdersViewModel = hiltViewModel(),        // ViewModel last, defaulted
) {
    val ux by viewModel.uiState.collectAsStateWithLifecycle()
    val latestOpen by rememberUpdatedState(onOpenOrder)
    val latestBack by rememberUpdatedState(onBack)
    DisposableEffect(viewModel) {
        viewModel.uiCallbacks = object : OrdersViewModelContract.UiCallbacks {
            override fun goToOrder(id: String) = latestOpen(id)
            override fun goBack() { latestBack?.invoke() }
        }
        onDispose { viewModel.uiCallbacks = null }
    }
    SetAppStatusBar(AppStatusBarConfig(title = stringResource(R.string.orders_title), onBack = onBack))
    SetAppSnackbar(ux.notice?.toSnackbarData(), onClose = viewModel::onNoticeDismissed)
    OrdersContent(ux = ux, onRowClick = viewModel::onRowClick, onRetry = viewModel::onRetry, modifier = modifier)
}

@Composable
private fun OrdersContent(ux: OrdersUxState, onRowClick: (String) -> Unit, onRetry: () -> Unit, modifier: Modifier) {
    when (ux) {
        OrdersUxState.Loading -> {{THEME_PREFIX}}LoadingFrame(modifier)
        is OrdersUxState.Failure -> {{THEME_PREFIX}}BlockingMessageScreen(/* title, message, retry action */)
        is OrdersUxState.Content -> LazyColumn(modifier, contentPadding = PaddingValues({{THEME_PREFIX}}Theme.dimens.screenEdge)) { /* rows */ }
    }
}
```

Resume forwarding (`onScreenActive` / `onScreenInactive`) and exit-flow collection are wired here too, as in
the reference screen (presentation reference, UI-09 and UI-13).

## 2. Structure

**DS-12** — The header (host app bar) is fixed, the body scrolls, and a footer of actions, when present, is
fixed; an action that belongs to the content flow (a form's submit) sits inside the scrolling body. *Why:* a
predictable frame keeps small screens usable and primary actions reachable.
<!-- origin: arch-spec DS-12 -->

- The gutter is `screenEdge`; gaps between items use `itemSpacing` / `contentSpacing` or the spacing scale.
- Empty, loading and failure are real states of the Ux model, each rendered deliberately (a loading frame, a
  blocking message with a retry, an empty-state message) — never a blank screen.
- Long text wraps; nothing assumes a width larger than the reference device.

## 3. Previews

**UI-12** — Previews render the stateless body from literal Ux states — one per meaningful state (content,
empty, failure, loading) — using `@ReferenceDevicePreview` for full screens and `@ReferenceWidthPreview` for
components, wrapped in the theme. *Why:* plain-data Ux states exist precisely so every state can be seen
without a running backend.
<!-- origin: arch-spec UI-12 -->

```kotlin
@ReferenceDevicePreview
@Composable
private fun OrdersEmptyPreview() = {{THEME_PREFIX}}Theme {
    OrdersContent(ux = OrdersUxState.Content(rows = emptyList()), onRowClick = {}, onRetry = {}, modifier = Modifier)
}
```

## 4. Branded surfaces

**DS-19** — Branded one-off surfaces (a splash, a marketing-style empty state) may use colors and geometry
outside the token set as `private` values in that screen's file; such colors never enter the Colors holder;
typography is not exempt. Full-bleed branded screens draw under the system bars and pad content with
`systemBarsPadding()`. *Why:* off-theme brand colors in the token set corrupt it for every other screen;
keeping them local keeps the exception visible.
<!-- origin: arch-spec DS-19 -->

## 5. System bars

**DS-21** — The Activity calls `enableEdgeToEdge`. With `light-only` it forces dark system-bar glyphs
(`SystemBarStyle.light(TRANSPARENT, TRANSPARENT)` for status and navigation bars, as `MainActivity` does);
with `light-dark` the automatic style follows the theme. *Why:* the default style picks glyph color from the
system dark setting, which draws white glyphs on a light-only app's white bars.
<!-- origin: arch-spec DS-21 -->
