# Pattern — live detail screen

## Problem

A detail screen shows a shared object that other people change while the user watches (participants join or
leave, states advance), plus running timers ("in progress for 12:34"). The screen must stay current while
visible, cost nothing while hidden, show timers that tick every second without a request per second, and
survive clock changes.

## Shape

```
:live (domain)
  LiveInteractor.load(id, policy): LiveOutcome
  data class LiveDetail(..., val elapsedAtAnchor: Duration, val anchorMillis: Long)   anchor = TimeSource.elapsedRealtimeMillis()
:live-ui
  LiveViewModel
    private var pollJob: Job? ; private var tickJob: Job?
    override fun onScreenActive()   { if (hasBeenActive) load(isEntry = false); hasBeenActive = true; startPolling(); startTicking() }
    override fun onScreenInactive() { pollJob?.cancel(); tickJob?.cancel() }
    State(detail, nowMillis, dialog: LiveDialog?, notice, ...)  → UxState with formatted timers
```

## Rules

- Polling SHOULD be **resume-gated**: `onScreenActive()` (ON_RESUME) starts `pollJob = viewModelScope.launch
  { while (isActive) { delay(POLL_INTERVAL); refresh() } }`; `onScreenInactive()` (ON_PAUSE) cancels it. Nothing
  polls while another screen or another app is in front.
- Poll ticks SHOULD use the non-refreshing request policy (DATA-19); user-driven reads (entry, retry, action
  results) may refresh credentials.
- Timers SHOULD be computed **locally from a monotonic anchor**: the domain stamps each successful read with
  `anchorMillis = timeSource.elapsedRealtimeMillis()` next to the server-reported elapsed duration; a `tickJob`
  advances only `nowMillis` once a second; displayed value = `elapsedAtAnchor + (now - anchorMillis)`. Never use the
  wall clock, never request per tick.
- Each new read re-anchors; the projection SHOULD clamp negative values to zero.
- Confirmation dialogs SHOULD live in `State` (`dialog: LiveDialog?`, a sealed type), not in composable
  `remember`, so they survive recomposition and are testable; confirm/dismiss are intents.
- Actions on the object (start, stop, add/remove participant) are idempotent writes (DATA-50) and, on success,
  trigger an immediate re-read rather than waiting for the next poll.
- When the object disappears or the user loses access mid-view, the screen leaves via the exit flow
  (`exit-flow.md`).
- The VM SHOULD skip a poll tick while a user-driven load or write is in flight.

## Pitfalls

- Polling in `init` or tied to composition: it keeps running behind other screens and after backgrounding.
- `System.currentTimeMillis()` for timers: jumps on clock changes and time-zone updates.
- One request per second to keep timers moving.
- A dialog held in composable state that disappears on configuration change.

## Test checklist

- [ ] `onScreenActive` → polls at the interval (virtual time); `onScreenInactive` → **no** requests over several
      intervals (absence + contrast pair)
- [ ] first `onScreenActive` after `init` does not double-load
- [ ] timer advances with `FakeTimeSource` without any request; re-anchors on the next read
- [ ] poll ticks use the non-refreshing policy; entry/retry use the refreshing one
- [ ] dialog shown/confirmed/dismissed via state; confirm sends one write with a stable request id
- [ ] object gone during a poll → exit emitted once
- [ ] poll suppressed while a write is in flight
