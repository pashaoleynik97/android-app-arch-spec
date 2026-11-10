# ADR-0004 — Back-stack SavedStateHandle for results; Scopes only for in-flow caches

Status: accepted · Area: UI-21, KERNEL-08..KERNEL-11

## Context

Screens exchange data in three ways: ids to open a screen, results returned to a caller ("for result"), and a
larger read model shared by the screens of one flow. Android may kill a backgrounded process and restore the
back stack later; only saved state (route arguments, `SavedStateHandle`) survives that.

## Decision

- Ids travel as **route arguments**.
- Results travel through the **previous back-stack entry's `SavedStateHandle`**, read on the entry's handle in
  the feature graph and cleared by the caller on consume.
- `Scopes` (a keyed in-memory holder) is used only for **caches** within a flow — data every consumer can
  re-read by id when the scope is missing.

## Consequences

- Results and navigation state survive process death; caches degrade to one extra read.
- Callers must clear result keys, or a later return replays the result.
- `Scopes` consumers carry a `null` branch (re-read or navigate back) and the flow root closes the scope.
- Passing objects through either transport is ruled out: references (ids, file paths) only.
