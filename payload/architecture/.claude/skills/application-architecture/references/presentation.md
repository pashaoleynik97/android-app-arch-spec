# Presentation — ViewModels, lifecycle, failures, navigation, permissions (component: architecture)

Spec: `.arch-spec/spec/specs/50-presentation.md`. Golden rules in `SKILL.md`: UI-01 (ViewModel shape), UI-06
(Context-free), UI-18 (one NavHost, navigation only in FeatureImpl). Screen composition (UI-11), previews and
host chrome are in the **compose-ui** skill. The reference screen is the `home` feature's
HomeViewModel / HomeViewModelContract / HomeScreen.

Contents: 1. ViewModel contract · 2. Lifecycle-gated work · 3. Failures and notices · 4. Navigation and
results · 5. Permissions and platform APIs · 6. Testability

---

## 1. ViewModel contract

**UI-02** — BaseViewModel stays thin (a protected `logTag` and an `onCleared` log line). ViewModelState is a
marker for private state classes, with `mState.upd { copy(…) }` over `MutableStateFlow` (atomic `update`).
No state machinery is added to the base class (ADR-0006 in the spec). *Why:* base-class frameworks force one
event model on every screen; the contract interface gives consistency without inheritance.
<!-- origin: arch-spec UI-02 -->

**UI-03** — `XViewModelContract.kt` holds all UI-facing types of the screen, in order: small enums / sealed
types for notices, failures, dialogs, exits; row Ux models (plain data, numbers pre-formatted where formatting
is a domain decision); `sealed interface XUxState { Loading; Content(…); Failure(kind) }` with derived
properties; then the contract interface (`UiCallbacks`, `uiState`, `uiCallbacks`, intents). *Why:* the
contract file is the screen's API; reviewers and previews need nothing else.
<!-- origin: arch-spec UI-03 -->

**UI-04** — The ViewModel keeps one private `data class State(…) : ViewModelState` with every field defaulted,
in `private val mState = MutableStateFlow(State())`, mutated only with `mState.upd { copy(…) }` on the main
thread. *Why:* one source of truth with atomic updates; no field is forgotten when state is rebuilt.
<!-- origin: arch-spec UI-04 -->

**UI-05** — `State.toUxState()` is the only place domain data becomes UI data. `uiState` is
`mState.map { it.toUxState() }` (or `combine(mState, otherFlow)` when a domain flow contributes) finished with
`.stateIn(viewModelScope, SharingStarted.Eagerly, <computed initial>)`. *Why:* one projection is
unit-testable and derived values cannot go stale; Eagerly with a computed initial keeps `uiState.value`
correct even in tests that never collect.
<!-- origin: arch-spec UI-05 -->

**UI-07** — Intents are `fun onX()` (`onSubmit`, `onRetry`, `onRefresh`, `onBackRequested`, `onScreenActive`,
`onScreenInactive`). An intent that starts work checks a guard first (`if (state.isSubmitting) return`). Forms
validate on submit; a field's error clears when that field changes; input filtering happens in the `onXChange`
intent. *Why:* double taps on slow devices are the most common source of duplicate writes; errors on every
keystroke show before the user finished typing.
<!-- origin: arch-spec UI-07 -->

**UI-08** — Navigation and other one-shot effects go through the `uiCallbacks` seam: the screen assigns it in
a `DisposableEffect(viewModel)` and clears it on dispose. A decision taken before callbacks attach (in `init`)
is buffered and flushed on attach. *Why:* a direct call cannot replay after rotation, cannot be lost before a
collector starts, and is asserted in tests with a recording UiCallbacks.
<!-- origin: arch-spec UI-08 -->

```kotlin
private var pendingNav: (XViewModelContract.UiCallbacks.() -> Unit)? = null
override var uiCallbacks: XViewModelContract.UiCallbacks? = null
    set(value) { field = value; if (value != null) pendingNav?.let { pendingNav = null; value.it() } }
private fun navigate(block: XViewModelContract.UiCallbacks.() -> Unit) {
    uiCallbacks?.block() ?: run { pendingNav = block }
}
```

**UI-09** — A navigation the user did not ask for (the item vanished, the flow completed elsewhere) is an exit
flow: `private val mExit = MutableSharedFlow<XExit>(replay = 1)` exposed as `val exit: SharedFlow<XExit>`,
collected by the screen in `LaunchedEffect(viewModel)`. Tests read `vm.exit.replayCache.firstOrNull()`.
*Why:* the decision may precede the collector, and a typed exit lets the feature graph pop the whole flow.
<!-- origin: arch-spec UI-09 -->

**UI-10** — Navigation arguments come from the injected SavedStateHandle, read once into a `private val`
(`savedStateHandle.get<String>(XRoutes.ARG_ID).orEmpty()`). Unknown enum-like tokens yield a Failure state,
never a crash. Tests pass `SavedStateHandle(mapOf(XRoutes.ARG_ID to "i-1"))`. *Why:* the handle survives
process death; reading once avoids string keys scattered through the ViewModel.
<!-- origin: arch-spec UI-10 -->

## 2. Lifecycle-gated work

**UI-13** — Loading recipe: load on entry from `init`; the screen forwards its back-stack entry's `ON_RESUME`
→ `onScreenActive()` and `ON_PAUSE` → `onScreenInactive()`; the ViewModel skips the first `onScreenActive`
(a `hasBeenActive` flag) and re-reads on later ones; a failed re-read keeps the content and raises a notice.
*Why:* returning from a sub-screen or the background must show fresh data; the first resume immediately
follows `init`.
<!-- origin: arch-spec UI-13 -->

**UI-14** — Polling, local ticking and collection of app-wide event streams run only while the destination is
RESUMED (started in `onScreenActive()`, cancelled in `onScreenInactive()`, or collected with
`repeatOnLifecycle(RESUMED)`). Elapsed-time displays tick from a monotonic anchor (TimeSource captured with
the server value), never the wall clock. *Why:* a covered screen must not react to input meant for the front
one or poll invisibly; wall-clock jumps make timers run backwards.
<!-- origin: arch-spec UI-14 -->

**UI-15** — One in-flight load per screen (`loadJob?.cancel(); loadJob = viewModelScope.launch { … }`). For
taps that start requests, last tap wins: ignore a repeat of the same id, cancel the previous job, drop stale
results with a generation counter, cancel on `onScreenInactive()`. *Why:* loads finishing out of order paint
old data over new; cancellation alone is not enough once a result is being applied.
<!-- origin: arch-spec UI-15 -->

## 3. Failures and notices

**UI-16** — A full-body `Failure(kind)` replaces content only when there is nothing to show; with content on
screen a failure becomes a notice. One notice slot per screen (`notice: XNotice?` in State), the latest wins,
`onNoticeDismissed()` clears it, and it is rendered through the host banner (`SetAppSnackbar`) by a private
`XNotice.toSnackbarData()`. Only a user-initiated refresh shows a spinner and its failure. *Why:* replacing
visible data with an error page punishes a transient blip; one slot avoids stacked banners.
<!-- origin: arch-spec UI-16 -->

**UI-17** — Outcomes the host owns (session ended, access to the app or its tenant scope lost) are swallowed
by screens: stop the spinner, do nothing else. *Why:* two layers reacting to one event means two navigations
and two banners.
<!-- origin: arch-spec UI-17 -->

## 4. Navigation and results

**UI-19** — Routes live in `object XRoutes` in the contract module: `const val ARG_ID`, a registered template
(`"x/details/{$ARG_ID}?mode={mode}"`) and a builder that encodes every segment with `Uri.encode`. Never
navigate to a template; routes carry ids only; optional arguments are query parameters declared nullable with
a `null` default; cross-module captions travel as opaque tokens the callee maps to its own strings; an
argument is named only through its `ARG_*` constant (template, builder, every reader). Type-safe
`@Serializable` routes were considered and rejected (spec ADR-0011) — do not migrate to them. *Why:*
payloads in routes break on size and process death; resource ids differ per module and build; one constant per
argument makes a rename a single compile-checked edit.
<!-- origin: arch-spec UI-19 -->

```kotlin
object XRoutes {
    const val ARG_ID = "id"
    const val DETAILS = "x/details/{$ARG_ID}"                       // template — registered
    fun details(id: String) = "x/details/${Uri.encode(id)}"         // builder — navigated to
}
```

**UI-20** — Destinations are flat `composable(…)` calls; non-root destinations pass the four AppTransitions
(`enter`, `exit`, `popEnter`, `popExit`). Re-rooting is `navigate(route) { popUpTo(navController.graph.id)
{ inclusive = true }; launchSingleTop = true }`. Repeated recipes become private extensions in the FeatureImpl;
a destination not built yet is a local stub screen, never a dead button. *Why:* one motion language, and
named recipes make back-stack surgery reviewable.
<!-- origin: arch-spec UI-20 -->

**UI-21** — "For result" navigation writes to the previous back-stack entry's SavedStateHandle and reads on the
caller's entry in the graph — the helpers in `:lib-android` `nav` package: a NavResultKey owned by the reading
screen, `navController.popBackStackWithResult(KEY, value)` on the writer side, `entry.ConsumeNavResult(KEY) { … }`
on the reader side (it clears the key after delivery). Values are references (an id, a path), never objects.
When leaving a multi-screen flow, pop the flow first, then write onto the entry the pop landed on.
*Why:* the handle survives process death; Scopes does not (ADR-0004 in the spec).
<!-- origin: arch-spec UI-21 -->

**UI-22** — No route guards in Core. Access control is done by the host (re-rooting on session end) and by
ViewModel-derived gates on the actions that need them. *Why:* an unused guard API advertises protection that
is not enforced.
<!-- origin: arch-spec UI-22 -->

## 5. Permissions and platform APIs

**UI-29** — Runtime permissions: the composable owns the launcher
(`rememberLauncherForActivityResult(RequestPermission())`), checks the grant in a `LaunchedEffect`, and reports
`viewModel.onPermissionResult(granted, permanentlyDenied)` with
`permanentlyDenied = !granted && !shouldShowRequestPermissionRationale(activity, permission)`. The ViewModel owns
the verdict (`Unknown → Granted | Denied | PermanentlyDenied`), projected to a Ux state with a "grant" action and,
when permanently denied, an "open app settings" action. No Activity-scoped permission helper. *Why:* a
per-destination launcher matches the Compose lifecycle; a global helper clobbers concurrent requests.
<!-- origin: arch-spec UI-29 -->

**UI-30** — A platform API with its own lifecycle (camera surface, media player) is owned by a composable
(`AndroidView` + a `DisposableEffect` that releases it); the ViewModel owns the decisions (what, where, single
in-flight guard, cleanup on failure). *Why:* platform objects bound to a lifecycle must not outlive the view;
decisions must stay testable.
<!-- origin: arch-spec UI-30 -->

## 6. Testability

**UI-31** — Decisions are extracted from ViewModels and composables into pure functions, `object` policies or
properties on data classes, each with a JVM test (a banner's auto-dismiss rule lives on TopSnackbarData).
*Why:* a rule a call site can forget gets forgotten; a rule on the model applies everywhere.
<!-- origin: arch-spec UI-31 -->

**UI-32** — A screen that renders an app-wide domain cache owns no data: `uiState` is
`combine(cache.snapshot, mState)` where `mState` holds only screen flags (refresh spinner, open sheet,
notice). *Why:* copying cache data into screen state creates a second source of truth that goes stale.
<!-- origin: arch-spec UI-32 -->
