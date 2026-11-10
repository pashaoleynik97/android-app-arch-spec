# Pattern — capture for result

## Problem

A flow needs one artefact produced by hardware — a photo, a scanned code — and then continues where it was.
The capture screen needs a runtime permission, a hardware API bound to a lifecycle, protection against double
capture, cleanup when capture fails, and a way to hand the result back that survives process death. It must stay
reusable by several callers without knowing them.

## Shape

```
:capture-ui-api  CaptureRoutes
                   CAPTURE = "capture?title={titleKey}"     opaque token, not a caption or resource id
                   fun capture(titleKey: String): String
                   const val RESULT_PATH = "capture.result.path"   owned by the reader, documented here for callers
:capture-ui      CaptureViewModel, CaptureScreen, CameraSurface (composable that owns the hardware API)
                 CaptureFeatureImpl: onCaptured = { path ->
                     navController.previousBackStackEntry?.savedStateHandle?.set(RESULT_PATH, path)
                     navController.popBackStack() }
caller           reads entry.savedStateHandle.getStateFlow<String?>(resultKey, null) in its FeatureImpl,
                 passes it to its screen with an onConsumed = { handle[resultKey] = null }
                 (a host-filled link type `CaptureLink(route: (titleKey) -> String, resultKey: String)` keeps route and key together)
```

## Rules

- The **VM owns the permission verdict** as a state machine: `enum Permission { Unknown, Granted, Denied,
  PermanentlyDenied }` → `UxState = Preparing | Ready(capturingToPath, notice) | PermissionRequired(permanentlyDenied)`.
  The composable launches the system request (`rememberLauncherForActivityResult(RequestPermission())`), computes
  "permanently denied" (`!granted && !shouldShowRequestPermissionRationale`) and reports
  `onPermissionResult(granted, permanentlyDenied)`. Permanently denied → an "open app settings" action.
- The **composable owns the hardware API** (camera provider, preview view, binding to the lifecycle, unbinding in
  `onDispose`). The VM never holds a `Context`, a camera or a view.
- A **single in-flight guard**: the VM allocates the destination file and sets `capturingToPath`; that one field is
  both the in-flight flag and the destination, so they cannot disagree. The composable triggers capture in
  `LaunchedEffect(capturingToPath)` exactly once per path and reports `onCaptureSucceeded/Failed/Unavailable`.
  Intents return early while a path is set.
- On failure the VM schedules deletion of the allocated file (the media store's deletion scheduler) and shows a
  one-slot notice.
- The result is a **reference** (file path, id), never bytes or objects, written on the **previous** entry's
  `SavedStateHandle`, then the callee pops.
- The caller reads the result from **its own** `NavBackStackEntry.savedStateHandle` in the graph (not from the
  VM's injected handle — a different instance), passes it to the screen, and **clears the key on consume** so a
  later return does not replay it.
- The callee maps known title tokens to its own strings; an unknown token gets a generic caption.
- Callers that need the capture route and result key get both through one host-filled link object; a `null` link
  leaves the action inert.

## Pitfalls

- Holding the hardware API in the VM (leaks the Activity; breaks on rotation).
- Two capture triggers racing because "in flight" and "destination" are separate fields.
- Reading the result from the VM's `SavedStateHandle` (never set) or not clearing it (replayed on next return).
- Passing captions or resource ids across module boundaries (couples callee strings to callers).
- Using `Scopes` for the result (lost on process death; saved state is not).

## Test checklist

- [ ] permission granted / denied / permanently denied → correct `UxState`; settings action only when permanent
- [ ] capture intent allocates one path; a second intent while set is ignored
- [ ] capture failure → deletion scheduled for that path, notice shown, path cleared
- [ ] success → callback with the path (recording `UiCallbacks`)
- [ ] unknown title token → generic caption
- [ ] caller side (VM): consuming a result calls the interactor once; a replayed `null` does nothing
