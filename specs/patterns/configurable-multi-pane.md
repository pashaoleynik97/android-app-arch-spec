# Pattern — configurable multi-pane screen

## Problem

The same interaction (collect a batch of attachments, describe them, review them) is needed in several flows with
different limits and options. Inside, the screen has several panes (list → metadata form → full-screen viewer)
that behave like sub-screens but share one piece of state. Building one screen per flow, or one destination per
pane, duplicates logic and splits state across back-stack entries.

## Shape

```
:batch-ui-api  BatchRoutes
                 BATCH = "batch/{ownerId}/{mode}?item={itemId}"   mode = opaque route token
                 const val MODE_A = "a"; const val MODE_B = "b"
                 fun batch(ownerId: String, mode: String, itemId: String? = null): String
:batch         data class BatchConfig(val mode: BatchMode, val maxEntries: Int, val isCommentEnabled: Boolean,
                                      val maxCommentLength: Int, val captureTitleKey: String, val uploadTarget: Target, …)
               interface BatchConfigProvider { fun configFor(mode: BatchMode): BatchConfig }
               enum class BatchMode { A, B; companion object { fun fromWire(token: String?): BatchMode? } }
:batch-ui      BatchViewModel  →  sealed interface BatchUxState { List(...); Metadata(...); Viewer(...); Failure(kind) }
               BatchScreen: when (uiState) { is List -> ListPane; is Metadata -> MetadataPane; is Viewer -> ViewerPane }
                            BackHandler { viewModel.onBackRequested() }
```

## Rules

- The route SHOULD carry an **opaque mode token**; the VM parses it with `BatchMode.fromWire(...)` and asks the
  `BatchConfigProvider` for the configuration. An unknown token → `Failure(UnsupportedMode)`, never a crash.
- "A second flow adds a mode and a config, not a screen": everything that differs between flows SHOULD be a
  config field. UI affordances (which buttons show) SHOULD be **derived from state + config**, not configured
  separately.
- Panes SHOULD be **variants of one `UxState`** in one destination; the VM holds one `State` (entries, selected
  entry, draft metadata, pane) and projects the active pane.
- **Back is the VM's to interpret**: the screen installs `BackHandler { viewModel.onBackRequested() }`; the VM
  moves Viewer → List, Metadata → List (with discard confirmation if the draft is dirty), and only from the root
  pane asks the host to leave (`uiCallbacks?.goBack()`).
- Optional route args (`itemId`) SHOULD be nullable query parameters so callers without them build the same
  route.
- Hardware capture SHOULD be delegated to the capture screen through a host-filled link
  (`capture-for-result.md`), so this module never depends on the capture feature.
- Callers provide route builders as lambdas through host seams, so they never learn this feature's mode
  vocabulary.

## Pitfalls

- One destination per pane: Back and state restoration become a navigation problem, and pane state is split.
- Booleans per flow sprinkled through composables instead of a config object.
- A system Back that pops the whole destination while a pane is open, losing the draft.
- Mode names leaking into callers as string literals.

## Test checklist

- [ ] each mode token → the expected config; unknown token → failure state
- [ ] limit from config enforced (add disabled at `maxEntries`)
- [ ] comment field present only when the config enables it; length clamped
- [ ] Back from Viewer/Metadata returns to List; from List calls `goBack` exactly once
- [ ] dirty metadata → Back asks for confirmation; clean → returns immediately
- [ ] captured result consumed once and appended to the list
