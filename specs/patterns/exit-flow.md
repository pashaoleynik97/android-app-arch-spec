# Pattern — exit flow (leaving without being asked)

## Problem

Some navigations are not requested by the user: the item vanished or was taken over (detected by a read, a poll
or a write), or the flow completed. The VM may decide this before the screen has started collecting (an `init`
read that fails fast), possibly several screens deep in the feature, and the screen the user lands on should be
able to explain why ("this item is no longer available").

## Shape

```kotlin
// contract
sealed interface WorkExit { data object Unavailable : WorkExit; data object Completed : WorkExit }
interface WorkViewModelContract { val exit: SharedFlow<WorkExit>; … }

// ViewModel
private val mExit = MutableSharedFlow<WorkExit>(replay = 1)       // decided before a collector may exist
override val exit: SharedFlow<WorkExit> = mExit.asSharedFlow()
private fun leave(exit: WorkExit) { if (mExit.replayCache.isEmpty()) mExit.tryEmit(exit) }

// Screen
val latestLeave by rememberUpdatedState(onLeave)
LaunchedEffect(viewModel) { viewModel.exit.collect { latestLeave(it) } }

// FeatureImpl (all NavController use lives here)
private fun leave(navController: NavHostController, exit: WorkExit) {
    navController.popBackStack(WorkRoutes.DETAILS, inclusive = true)              // pop the whole feature
    if (exit == WorkExit.Unavailable && unavailableResultKey != null)
        navController.currentBackStackEntry?.savedStateHandle?.set(unavailableResultKey, true)  // flag the landing entry
}
```

`unavailableResultKey` is a host seam: the landing screen's feature owns the key (the reader owns its key) and
the host passes it in.

## Rules

- Unrequested leaves SHOULD go through a `MutableSharedFlow<Exit>(replay = 1)`, not a nullable state field and not
  `UiCallbacks`: `replay = 1` keeps a decision made before the collector attaches; it is emitted **at most once**
  per VM.
- The exit type SHOULD be a small sealed type naming *why* the screen leaves; the VM never knows *where* to.
- The feature impl SHOULD pop to and including the **feature root** (`popBackStack(ROOT, inclusive = true)`), then
  write the result flag on `currentBackStackEntry` — the entry the pop landed on. Writing on
  `previousBackStackEntry` before popping addresses the wrong entry from deeper screens.
- The landing screen SHOULD read the flag with `getStateFlow(key, false)`, show a one-slot notice, clear the key on
  consume, and (if it shows a cached list) trigger a refresh.
- A `null` result-key seam leaves the leave working, just without the notice.
- Host-owned outcomes (session expired, app-wide access loss) SHOULD NOT emit an exit — the host is navigating
  already (DATA-18).
- Requested navigation (Back, "go to item") stays on `UiCallbacks`.

## Pitfalls

- `replay = 0`: an exit decided during `init` is lost and the user stays on a dead screen.
- Emitting twice (a poll and a write both detect the loss) → double pop.
- Writing the result flag before popping, or on the wrong entry.
- Exiting on `SessionExpired` and racing the host's own navigation to sign-in.

## Test checklist

- [ ] item gone on entry read → `vm.exit.replayCache.firstOrNull() == Unavailable` (no collector needed)
- [ ] gone detected by poll and by write → exactly one exit
- [ ] completed → `Completed` exit
- [ ] session expired → no exit
- [ ] landing VM: flag `true` → notice + refresh; key cleared after consume
