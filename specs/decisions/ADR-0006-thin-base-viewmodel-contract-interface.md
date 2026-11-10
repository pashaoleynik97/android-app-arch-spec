# ADR-0006 — Thin BaseViewModel plus a per-screen contract interface

Status: accepted · Area: UI-01..UI-09

## Context

Many codebases put a state/event/effect framework into a `BaseViewModel<S, E, F>` (MVI style). That forces one
event model on every screen, couples tests to the framework, and grows with every special case.

## Decision

- `BaseViewModel` holds only `logTag` and an `onCleared` log line; `ViewModelState` is a marker plus an `upd {}`
  helper.
- Each screen declares `XViewModelContract` (`uiState: StateFlow<XUxState>`, `var uiCallbacks`, intent methods)
  and its ViewModel implements it with a private `State` projected by `toUxState()`.
- One-shot navigation goes through the `uiCallbacks` seam (with a pending flush); unrequested leaves use an exit
  `SharedFlow(replay = 1)`.

## Consequences

- Consistency comes from the contract shape and the feature template, not from inheritance.
- Tests drive intents and assert on `uiState.value` and a recording `UiCallbacks` — no framework fixtures.
- A little boilerplate per screen (the contract file) is accepted in exchange for screens that read on their own.
