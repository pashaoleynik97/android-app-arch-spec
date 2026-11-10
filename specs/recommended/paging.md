# Recommended: paging — server-paginated lists (PAGE)

Tier: **Recommended** (component `paging`). SHOULD / MAY guidance with a *why* per rule. Decision record:
`../decisions/ADR-0001-own-pager-over-paging-library.md`. Screen recipe: `../patterns/paginated-picker.md`.

The component adds new files to two Core modules (no new module): `Pager`, `PagingConfig`, `Page`,
`PageLoadOutcome`, `PageSource`, `PagingState` in `:lib-common` (`{{ROOT_PACKAGE}}.libcommon.paging`), and
`PagingScrollTrigger` + the `pagingFooter` / `<Prefix>PagingLoadingItem` / `<Prefix>PagingRetryItem` footer items in
`:lib-android` (`…libandroid.ui.compose.paging`), with the full JVM test suite of the pager.

## When to use

- A screen lists a **server-side, page-by-page** collection (`?page=&limit=` or a cursor) that is too large to load
  at once, often with a live search box.
- Rows are decorated from a second source (selection, "already added", a cached status) — the plain
  `StateFlow<PagingState>` projects into the screen's own `UxState` like any other data.
- You want every paging rule pinned by deterministic JVM tests.

## When not to

- The list is small and bounded (one request returns everything): a plain load is simpler.
- The data is an **offline-first database feed** (Room as the source of truth with a remote mediator): the AndroidX
  Paging library's `RemoteMediator` fits better — record that decision in a new ADR.
- Infinite feeds with bidirectional paging (load *before* the first page): the pager appends only.

---

## 1. Engine rules

**PAGE-01** — A paginated screen SHOULD own exactly one `Pager<K, T, F>` per list, created on `viewModelScope`, and
SHOULD call `submitKey(initialKey)` before `uiState`'s eager initial value is computed. The screen state is
`combine(mState, pager.state) { s, p -> s.toUxState(p) }`.
*Why:* the pager's state is confined to its scope's dispatcher (no locks); submitting after `stateIn` computed its
initial value flashes an empty "no results" state.

**PAGE-02** — Loads SHOULD be single-flight, and the **only** scroll input SHOULD be
`onLastVisibleIndex(index)`, fed by `PagingScrollTrigger(listState, viewModel::onLastVisibleIndex)`. The "close
enough to the end" threshold lives in `PagingConfig.prefetchDistance`, not in the composable.
*Why:* one copy of the threshold, unit-tested; a trigger computed in composition fires on every frame of a fling and
starts duplicate requests.

**PAGE-03** — A key change or refresh SHOULD bump a **generation** and cancel the running load; a response stamped
with an older generation is dropped. Keys SHOULD have value equality; an equal key is a no-op. A search key SHOULD be
normalised (trim, blank → `null`) and debounced in the ViewModel before `submitKey`.
*Why:* cancellation alone is not enough — a response already being applied would paint page 2 of "ab" under the
results for "abc"; equality is what lets the VM call `submitKey` on every debounced keystroke.

**PAGE-04** — A refresh SHOULD clear items, counter and failures before loading. A **first-page** failure
(`refreshFailure`) replaces the body with the screen's failure state; an **append** failure (`appendFailure`) keeps
the loaded rows, shows a footer with retry, and blocks further appends until `retry()`.
*Why:* stale rows under a new key mislead; an append failure that re-fires on every scroll nudge hammers a dead
network while the user is still reading a usable list.

**PAGE-05** — The failure type `F` SHOULD be **feature-owned** (a small sealed type), mapped from the feature's
classified service result inside the `PageSource` lambda — never an HTTP or Retrofit type.
*Why:* the error model is classified once in the data layer (DATA rules); the pager only carries the verdict to the
ViewModel, which maps it to copy.

**PAGE-06** — Lists whose ordering can shift between page requests (inserts on a live list) SHOULD pass
`itemKey = { it.id }` so appended rows already loaded are dropped.
*Why:* offset paging over a changing list returns a row twice when one is inserted before the boundary; duplicate
keys also crash `LazyColumn` when rows are keyed.

**PAGE-07** — `PagingConfig` SHOULD be provided through DI under a **per-screen qualifier**
(`@Provides @OrdersPaging fun pagingConfig() = PagingConfig(pageSize = 30, prefetchDistance = 10)`), with
`firstPage` matching the backend (`1` for the example contract; `0` for 0-based APIs).
*Why:* the next paginated screen binds its own tuning without touching the first; an off-by-one first page silently
skips or repeats the first page.

**PAGE-08** — `hasNext` SHOULD come from the response's pagination block, the next page SHOULD be derived from the
page that was **requested** (not the one echoed), and an **empty page ends the list** even when the server claims
more.
*Why:* `items.size == pageSize` is wrong whenever the last page is full; an echoed page number from a buggy server
could make the pager skip or loop; an empty page with `hasNext = true` would spin forever.

**PAGE-09** — Row decorations from a second source (selection, "already added", in-flight state) SHOULD be derived
in the projection (`toUxState`), never stored on the paged items.
*Why:* the pager owns the items; a decoration stored on them goes stale the moment the second source changes and
needs a reload to fix.

**PAGE-10** — A `PageSource` MAY have side effects (record a cache, a "last seen" marker); the pager ignores a
superseded *result* but does not prevent the call. A side effect that depends on the key or page SHOULD be guarded
by the source itself.
*Why:* cancellation is cooperative — a load may already have written before it was superseded.

## 2. UI rules

**PAGE-11** — The list SHOULD render the footer from the pager state (`pagingFooter(state, …)`: loading item while
appending, retry item on append failure, nothing otherwise) as the **last** `LazyColumn` item with one stable key,
and the body SHOULD use the screen's normal loading / failure / empty states for the first page.
*Why:* swapping loading ↔ retry under one key keeps the scroll position; a footer for a first-page failure would
sit under an empty list.

**PAGE-12** — Footer texts SHOULD come from the feature's strings; the footer look comes from theme tokens only.
*Why:* features own their copy (DS-20); a redesign then changes one file in `:lib-android`.

## 3. Testing

**PAGE-13** — Pager behaviour SHOULD be tested with a **hand-answered fake source** (each load awaits a
`CompletableDeferred` the test completes) and the pager on its own `CoroutineScope(StandardTestDispatcher(scheduler))`
cancelled in `finally`. A screen using the pager tests its VM with the same fake: debounce in virtual time, append on
threshold, retry, de-duplication.
*Why:* "what happens while a load is in flight" is where single-flight and the generation guard live; answering by
hand makes it deterministic instead of timing luck (TEST-14).

## Adapting this to your app

- **Keep:** the engine and its tests unchanged — they are domain-free. Extend by adding methods with tests (e.g.
  `invalidate(item)`), never by weakening a rule.
- **Adapt:** `PagingConfig` defaults (`pageSize`, `prefetchDistance`, `firstPage`) to your backend; the footer
  composables' look to your design (signatures stay).
- **Cursor APIs:** keep `K` as the query key and carry the cursor inside the source — e.g. a `PageSource` that maps
  the requested page number to the cursor it received for the previous page (reset on page `firstPage`).
- **Example-only:** the 1-based `page` + `limit` parameters and the `hasNextPage` / `itemCount` pagination block are
  the example contract (see `mitm-testing`'s fake backend, `GET v1/items`).
