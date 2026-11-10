# ADR-0001 — Own Pager instead of the AndroidX Paging library

Status: accepted · Area: KERNEL / Recommended `paging` (`specs/recommended/paging.md`)

## Context

Paginated lists need: single-flight loading, a live search key that may change mid-flight, retry of a failed
append without losing loaded rows, and rows that are **decorated by a second, independent source** (e.g. which
items are already selected) after loading. The AndroidX Paging library models data as an opaque
`PagingData` stream rendered through `LazyPagingItems`; its state cannot be combined into a plain screen
`UxState`, transforms on loaded pages are awkward, and its behaviour is hard to drive deterministically in JVM
tests.

## Decision

The project uses a small, kernel-level `Pager<K, T, F>` (shipped by the `paging` component) that exposes a plain
`StateFlow<PagingState<T, F>>` (items, refreshing/appending flags, end reached, refresh/append failure). Its rules:
single flight; a generation counter drops stale responses; an equal key is a no-op; refresh clears first; append
failure keeps items and waits for explicit retry; an empty page ends the list; the failure type `F` is
feature-owned (never HTTP); optional de-duplication by item key. The only scroll input is
`onLastVisibleIndex(index)`, fed by a small Compose trigger.

## Consequences

- Screens project `combine(mState, pager.state)` into their own `UxState` like any other data (UI-05).
- Every rule is a unit test against a hand-driven fake source (each load completed by the test).
- The project maintains ~200 lines of paging code and does not get Paging's built-in database mediator; an
  app whose main data is an offline-first database feed should reconsider (record a new ADR).
