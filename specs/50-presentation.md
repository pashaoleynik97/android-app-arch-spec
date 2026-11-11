# 50 — Presentation: ViewModels, screens, lifecycle, navigation, host chrome (UI)

Tier: **Core** (component `architecture`). Types named here: `BaseViewModel`, `ViewModelState` (`:lib-android`),
`FeatureApi`, `FeaturesHolder` (`:feature-api`), host-chrome controllers and `Set…` effects (`:lib-android`).
Visual rules (tokens, kit) are in `55-design-system.md`; domain outcomes and errors in `60-data-and-errors.md`.

Contents: 1. ViewModel contract · 2. Screen composition · 3. Lifecycle-gated work · 4. Failures and notices ·
5. Navigation · 6. Host chrome · 7. Permissions and platform APIs · 8. Testability

---

## 1. ViewModel contract

**UI-01** — Every screen has one ViewModel declared as
`@HiltViewModel class XViewModel @Inject constructor(…) : BaseViewModel(), XViewModelContract`.
*Why:* one shape for every screen means an agent can read or write any screen by analogy, and tests target the
contract the screen actually uses.

**UI-02** — `BaseViewModel` stays **thin**: `protected val logTag` and an `onCleared` log line, nothing else.
`ViewModelState` is a marker interface for private state classes, with an `upd { copy(…) }` helper over
`MutableStateFlow` (which delegates to the atomic `update {}`). State machinery is not added to the base class
(ADR-0006).
*Why:* base-class frameworks force every screen into one event model; the contract interface gives the same
consistency without inheritance.

**UI-03** — `XViewModelContract.kt` holds **all** UI-facing types of the screen, in this order:
1. small enums / sealed types for message slots and variants (`XNotice`, `XFailureKind`, `XDialog`, `XExit`);
2. row/section UI models (`XRowUx`) — plain data, numbers pre-formatted as `String` where formatting is a domain
   decision;
3. `sealed interface XUxState { data object Loading; data class Content(…); data class Failure(val kind) }` with
   derived properties on the Ux classes (`val isEmpty get() = rows.isEmpty()`);
4. the contract:
```kotlin
interface XViewModelContract {
    interface UiCallbacks { fun goToDetails(id: String); fun goBack() }
    val uiState: StateFlow<XUxState>
    var uiCallbacks: UiCallbacks?
    fun onRetry()
    fun onRowClick(id: String)
    fun onNoticeDismissed()
}
```
*Why:* the contract file is the screen's API; reviewers and previews need nothing else.

**UI-04** — The ViewModel keeps a single private `data class State(…) : ViewModelState` holding domain models and
screen flags, **every field defaulted in the constructor**, held in `private val mState = MutableStateFlow(State())`
and mutated only with `mState.upd { copy(…) }` from the main thread.
*Why:* one source of truth with atomic updates; no field can be forgotten when a new state is built.

**UI-05** — `State.toUxState(…)` is the **only** place domain data becomes UI data. `uiState` is
`mState.map { it.toUxState() }` — or `combine(mState, otherFlow) { … }` when a domain flow (cache, pager, device
availability) contributes — finished with `.stateIn(viewModelScope, SharingStarted.Eagerly, <computed initial>)`.
*Why:* a single projection is unit-testable and keeps derived values from being stored and going stale;
`Eagerly` with a computed initial means `uiState.value` is always correct, including in tests that never collect.

**UI-06** — ViewModels are **Context-free**: no `Context`, `Resources`, string resource lookups, `NavController`,
or Compose types. Ux types hold enums, ids and plain data; the composable maps enums to `stringResource(…)`.
*Why:* Context-free VMs run in plain JVM tests and survive configuration changes without leaking an Activity.

**UI-07** — Intents are `fun onX()` methods (`onSubmit`, `onRetry`, `onRefresh`, `onBackRequested`,
`onScreenActive`, `onScreenInactive`). An intent that starts work checks a guard first
(`if (state.isSubmitting) return`). Form validation runs **on submit**; a field's error clears when that field
changes; input filtering (digits only, max length) happens in the `onXChange` intent.
*Why:* double taps on slow devices are the most common source of duplicate writes; validating on every keystroke
shows errors before the user has finished typing.

**UI-08** — Navigation and other one-shot side effects go through the `uiCallbacks` seam, not through channels or
event flows in the state. The screen assigns it in a `DisposableEffect(viewModel)` and clears it `onDispose`.
A ViewModel that may decide before the callbacks are attached (a decision made in `init`) buffers it and flushes
on attach:
```kotlin
private var pendingNav: (XViewModelContract.UiCallbacks.() -> Unit)? = null
override var uiCallbacks: XViewModelContract.UiCallbacks? = null
    set(value) { field = value; if (value != null) pendingNav?.let { pendingNav = null; value.it() } }
private fun navigate(block: XViewModelContract.UiCallbacks.() -> Unit) {
    uiCallbacks?.block() ?: run { pendingNav = block }
}
```
*Why:* a direct call cannot be replayed twice after rotation, cannot be lost before a collector starts, and is
trivially asserted in tests with a recording `UiCallbacks`.

**UI-09** — A navigation the user did **not** ask for (the item vanished, the flow completed elsewhere) is an
**exit flow**: `private val mExit = MutableSharedFlow<XExit>(replay = 1)` exposed as `val exit: SharedFlow<XExit>`,
collected by the screen in `LaunchedEffect(viewModel)` which calls the matching navigation lambda. Tests read
`vm.exit.replayCache.firstOrNull()`.
*Why:* the decision may precede the collector (`replay = 1` keeps it), and a typed exit lets the feature graph
pop the whole flow and leave a result for the landing screen.

**UI-10** — Navigation arguments come from the injected `SavedStateHandle`, read **once** into a `private val`
(`savedStateHandle.get<String>(XRoutes.ARG_ID).orEmpty()`; optional ones `?.takeIf { it.isNotBlank() }`). Enum-like
tokens are parsed by a domain helper; an unknown token yields a `Failure` state, not a crash. Tests pass
`SavedStateHandle(mapOf(XRoutes.ARG_ID to "…"))`.
*Why:* the handle is restored after process death; reading once avoids scattering string keys through the VM.

## 2. Screen composition

**UI-11** — Each destination is a public stateful `XScreen` and a private stateless body (`XContent` /
`XScaffold`):
```kotlin
@Composable
fun XScreen(
    onNavigateToDetails: (id: String) -> Unit,          // navigation lambdas first
    modifier: Modifier = Modifier,
    itemGone: Boolean = false,                          // results read in the graph (UI-21)
    onItemGoneConsumed: () -> Unit = {},
    viewModel: XViewModel = hiltViewModel(),            // ViewModel last, defaulted
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val latestToDetails by rememberUpdatedState(onNavigateToDetails)
    DisposableEffect(viewModel) {
        viewModel.uiCallbacks = object : XViewModelContract.UiCallbacks {
            override fun goToDetails(id: String) = latestToDetails(id)
        }
        onDispose { viewModel.uiCallbacks = null }
    }
    // results → LaunchedEffect(key); lifecycle → onScreenActive/Inactive (UI-13);
    // host chrome → SetAppStatusBar / SetAppSnackbar (UI-24); exit flow (UI-09)
    XScaffold(uiState = uiState, onRowClick = viewModel::onRowClick, onRetry = viewModel::onRetry, modifier = modifier)
}
```
The stateless body takes the Ux state and lambdas only — **never the ViewModel**; intents are passed as
method references; every lambda captured by an effect goes through `rememberUpdatedState`.
*Why:* the stateless body is previewable and recomposes predictably; passing the VM down hides which intents a
component can fire.

**UI-12** — Previews render the stateless body from literal Ux states, one preview per meaningful state (content,
empty, failure, loading), sized to the reference device (DS-11).
*Why:* plain-data Ux states exist precisely so every state can be seen without a running backend.

## 3. Lifecycle-gated work

**UI-13** — Loading follows one recipe:
- **load on entry** from `init { load(isEntry = true) }`;
- **re-read on resume**: the screen forwards its back-stack entry's `ON_RESUME` → `onScreenActive()` and
  `ON_PAUSE` → `onScreenInactive()`; the VM skips the first `onScreenActive` (`hasBeenActive` flag) so entry does
  not load twice;
- a re-read keeps the current content on failure (only a notice is raised).
*Why:* returning from a sub-screen or from the background must show fresh data, and the first resume
immediately follows `init`.

**UI-14** — Polling, local ticking, and collection of app-wide event streams (hardware input, broadcast events)
run **only while the destination is RESUMED**: started in `onScreenActive()` and cancelled in
`onScreenInactive()`, or collected with `repeatOnLifecycle(RESUMED)` in a `CollectWhileResumed(flow) { … }`
helper. Elapsed-time displays tick from a **monotonic anchor** (`TimeSource.elapsedRealtimeMillis()` captured
with the server value), never from the wall clock.
*Why:* a screen under another screen must not react to input meant for the front one or poll invisibly; wall-clock
jumps make timers run backwards.

**UI-15** — One in-flight load per screen (`loadJob?.cancel(); loadJob = viewModelScope.launch { … }`). For taps
that start requests, **last tap wins**: ignore a repeat of the same id, cancel the previous job, and drop stale
results with a generation counter; cancel on `onScreenInactive()`.
*Why:* concurrent loads finishing out of order paint old data over new; cancellation alone is not enough when a
result is already being applied.

## 4. Failures and notices

**UI-16** — A full-body `Failure(kind)` state replaces the content **only when there is nothing to show**; with
content on screen, a failure becomes a **notice**. Each screen has **one notice slot** (`notice: XNotice?` in
`State`), the most recent notice wins, and `onNoticeDismissed()` clears it. The notice is rendered through the
host snackbar (`SetAppSnackbar`, UI-24) by a private `@Composable fun XNotice.toSnackbarData()`. Only
user-initiated refresh shows a refresh spinner and its failure notice; silent refreshes stay silent.
*Why:* replacing visible data with an error page punishes a transient blip; one slot avoids stacked banners and
competing dismiss logic.

**UI-17** — Outcomes the **host** owns (session ended, access to the whole app or its current tenant scope lost)
are swallowed by screens: the VM stops its spinner and does nothing else, because the host is already navigating
away and showing its own notice. Screens render only outcomes that are about their own content.
*Why:* two layers reacting to one 401 means two navigations and two banners.

## 5. Navigation

**UI-18** — There is **one** `NavHost`, in the host, using the stock `NavHostController` (navigation-compose,
string routes). Features attach destinations by implementing `FeatureApi.registerGraph(…)` and being registered
in `FeaturesHolder` at startup (DI-15); the host iterates `FeaturesHolder.features()` inside `NavHost { }`.
**All `NavController` code lives in `XFeatureImpl`**: screens get plain lambdas, ViewModels never see
navigation. The `FeatureApi` contract carries only what features use (the graph builder and the controller) —
no speculative hooks.
*Why:* navigation is wiring; keeping it in one file per feature makes the flow readable and keeps screens
reusable and testable.

**UI-19** — Routes live in `object XRoutes` in `:x-ui-api`:
```kotlin
object XRoutes {
    const val ARG_ID = "id"
    const val ARG_MODE = "mode"
    const val DETAILS = "x/details/{$ARG_ID}?$ARG_MODE={$ARG_MODE}"          // template — registered
    fun details(id: String, mode: String? = null) = buildString {           // builder — navigated to
        append("x/details/${Uri.encode(id)}")
        if (mode != null) append("?$ARG_MODE=${Uri.encode(mode)}")
    }
    const val RESULT_ITEM_GONE = "x.result.item-gone"                       // owned by the screen that READS it
}
```
- never navigate to a template; builders encode every segment (`Uri.encode`); requests use the decoded id;
- **routes carry ids only**, never payloads — enough to re-read after process death;
- optional arguments are **query parameters** declared `nullable = true, defaultValue = null`, so callers
  without the value build the same route;
- cross-module captions travel as opaque tokens the callee maps to its own strings, never as text or resource ids;
- an argument is named only through its `ARG_*` constant — in the template, the builder and every reader
  (`entry.arguments`, `SavedStateHandle`); no string literal names an argument elsewhere.

String routes are a recorded choice: type-safe `@Serializable` routes were considered and rejected (ADR-0011).
*Why:* payloads in routes break on size and process death; resource ids differ per module and build; with one
constant per argument a rename is a single compile-checked edit, which is the guarantee the rejected type-safe
routes would have added.

**UI-20** — Destinations are flat `composable(…)` calls (no nested graphs by default). Non-root destinations use
the shared `AppTransitions` (`enter`, `exit`, `popEnter`, `popExit`) from `:lib-android`. Re-rooting uses
`navigate(route) { popUpTo(navController.graph.id) { inclusive = true }; launchSingleTop = true }`. Repeated
recipes become private extension functions in the `XFeatureImpl`. A destination that is not built yet is a
local stub screen, never a dead button.
*Why:* one transition set keeps motion consistent; named recipes make back-stack surgery reviewable.

**UI-21** — "For result" navigation uses the **previous back-stack entry's `SavedStateHandle`** (ADR-0004):
```kotlin
// callee FeatureImpl
onDone = { path -> navController.previousBackStackEntry?.savedStateHandle?.set(resultKey, path); navController.popBackStack() }
// caller FeatureImpl, inside composable(…) { entry -> }
val captured by entry.savedStateHandle.getStateFlow<String?>(resultKey, null).collectAsStateWithLifecycle()
CallerScreen(captured = captured, onCapturedConsumed = { entry.savedStateHandle[resultKey] = null })
```
- read on the **entry's** handle in the graph, not the ViewModel's injected handle (a different instance);
- the caller **clears the key on consume**, or a later return replays it;
- pass references (an id, a file path), never objects or bytes;
- when leaving a multi-screen flow, pop the whole flow first, then write onto `currentBackStackEntry` (the entry
  the pop landed on) — `previousBackStackEntry` addresses only one step down;
- the key constant is owned by the screen that reads it; across siblings, the host passes it (MOD-18).
*Why:* the handle survives process death; `Scopes` does not.

**UI-22** — Core does not ship route guards. Access control is done by the host (re-rooting on session end,
UI-25) and by VM-derived gates on the actions that need them. A project that later needs per-route policies adds
them as a reviewed extension of `FeaturesHolder` with real call sites.
*Why:* an unused guard API advertises a protection that is not actually enforced.

## 6. Host chrome

**UI-23** — Chrome drawn by the host outside the `NavHost` (app bar, screen banner slot, app menu) is controlled
by screens through **controllers provided as CompositionLocals**:
```kotlin
@Stable class AppStatusBarController { var config: AppStatusBarConfig? by mutableStateOf(null); private set /* … */ }
val LocalAppStatusBarController = staticCompositionLocalOf<AppStatusBarController> {
    error("AppStatusBarController not provided. Wrap the NavHost with a CompositionLocalProvider.")
}
```
Locals have **no default** (fail fast). Controller state has a private setter; screens use declarative
`Set…` effects (`SetAppStatusBar(config)`, `SetAppSnackbar(data, onClose, onDismiss, onRetry)`,
`SetAppMenuOptions(…)`), never the controller directly.
*Why:* screens declare what they need; the host decides how to draw it; a missing provider is a crash in
development, not a silently missing bar in production.

**UI-24** — `Set…` effects apply **only while the destination is RESUMED** (checked on entry and on each
`ON_RESUME`), so an outgoing screen cannot win the last-writer race during a transition. The status bar is not
hidden on dispose (the incoming screen overwrites it — no flicker). The snackbar slot is **owned by the raiser**:
`hide(request)` clears only if the slot still holds that same request, and a screen withdraws its own request on
dispose.
*Why:* during a transition both destinations are composed; without RESUMED gating and ownership checks, the
leaving screen clears or overwrites the arriving screen's chrome.

**UI-25** — When the host re-roots navigation (session ended → login), it MUST explicitly reset chrome state that
outlives destinations (close the menu, clear stale options). A host action that only makes sense in a live
session is gated by a VM-derived flag from the host shell ViewModel, not by a screen's opt-in.
*Why:* chrome lives outside the `NavHost` and survives re-rooting; options last written by a now-popped screen
go stale.

**UI-26** — Host notices are layered by nesting `TopSnackbarHost`s outside the `NavHost`, **outermost = highest
priority** (e.g. session notices > connectivity banner > screen banner). VM notice enums map to `TopSnackbarData`
in private `@Composable` extension functions in the host. Screens MUST NOT host their own `TopSnackbarHost`.
*Why:* a host anchors its banner to its own box; a screen-local host draws under the app bar instead of over it,
and priority by nesting needs no arbitration code.

**UI-27** — The Activity is edge-to-edge. The host pads the `NavHost` from the navigation bar **once**
(`navigationBarsPadding()`) and consumes status-bar insets when the app bar is shown; screens do not pad for
system bars again (full-bleed branded screens are the exception, DS-19).
*Why:* insets applied at two levels double the gap on some devices and vanish on others.

**UI-28** — The host shell ViewModel emits **host** navigation events (go to login) on
`MutableSharedFlow(replay = 0, extraBufferCapacity = 1)` collected in `LaunchedEffect(navController)`.
*Why:* unlike a screen's exit (UI-09), a host event must fire only while the host is collecting — a replayed
"go to login" after re-composition would wrongly re-navigate.

## 7. Permissions and platform APIs

**UI-29** — Runtime permissions follow the Compose-native pattern:
- the **composable** owns the launcher: `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`;
  it checks the current grant in a `LaunchedEffect`, launches when needed, and reports
  `viewModel.onPermissionResult(granted, permanentlyDenied)` where
  `permanentlyDenied = !granted && !shouldShowRequestPermissionRationale(activity, permission)`;
- the **ViewModel** owns the verdict as a state machine (`Unknown → Granted | Denied | PermanentlyDenied`)
  projected to Ux (`PermissionRequired(permanentlyDenied)`), with a "grant" action and, when permanently denied,
  an "open app settings" action (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).
No Activity-scoped global permission helper is used.
*Why:* a per-destination launcher matches the Compose lifecycle; an Activity-scoped helper with one pending
request clobbers concurrent requests and must be registered before `STARTED`.

**UI-30** — A platform API with its own lifecycle (camera surface, media player) is owned by a composable
(`AndroidView` + `DisposableEffect` that releases it); the ViewModel owns decisions (what to capture, where to
store, single in-flight guard, cleanup on failure). One state field serves as both in-flight flag and target
(`capturingToPath: String?`) when they cannot disagree.
*Why:* platform objects bound to a `LifecycleOwner` must not outlive the view; decisions must stay testable.

## 8. Testability

**UI-31** — Decisions are extracted from ViewModels and composables into pure functions, `object` policies or
properties on data classes, and unit-tested on the JVM (a banner's auto-dismiss rule lives on its data class;
a "should this transition show a banner?" decision lives in a policy object returning a sealed action).
*Why:* a rule a call site can forget is a rule that gets forgotten; a rule on the model is enforced everywhere
and costs one JVM test.

**UI-32** — A screen that renders an app-wide domain cache owns **no data**: its `uiState` is
`combine(cache.snapshot, mState)` where `mState` holds only screen flags (pull-refresh spinner, open sheet,
notice).
*Why:* copying cache data into screen state creates a second source of truth that goes stale.
