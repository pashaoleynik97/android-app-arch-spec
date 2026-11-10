# Pattern — paginated search picker

## Problem

The user picks entries from a large server-side list with a search box ("add a person"). The list must load page
by page as the user scrolls, restart cleanly when the query changes, not fire a request per keystroke, and let the
user act on several rows quickly — each row action being a write that must not be duplicated.

## Shape

```
:lib-common   Pager<Key, Item, Failure>(scope, config: PagingConfig, source: suspend (Key, page, pageSize) -> PageLoadOutcome,
                                        itemKey: (Item) -> Any)
              PagingConfig(pageSize, prefetchDistance); pager.state: StateFlow<PagingState>; submitKey/onLastVisibleIndex/retry
:lib-android  PagingScrollTrigger(listState, onLastVisibleIndex) + footer items (loading / error+retry / end)
:picker       PickerInteractor.loadPage(query, page, size): PickerFetchResult; addEntry(rowId, attempt): AddOutcome
              @Qualifier annotation class PickerPaging ; @Provides @PickerPaging fun pagingConfig(): PagingConfig
:picker-ui    PickerViewModel(interactor, @PickerPaging pagingConfig, savedStateHandle)
```

(The `Pager` and paging UI pieces ship with the Recommended `paging` component — `specs/recommended/paging.md`.)

## Rules

- The VM SHOULD own one `Pager` on `viewModelScope`, call `submitKey(initialKey)` **before** `uiState`'s eager
  initial value is computed, and combine: `uiState = combine(mState, pager.state) { s, p -> s.toUxState(p) }`.
- The search key SHOULD be derived from state and debounced:
  `mState.map { it.query.toSearchKey() }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE)` collected into
  `pager.submitKey(it)`. Normalise the query (trim, blank → null) inside `toSearchKey()` so whitespace edits do not
  restart paging.
- `itemKey` SHOULD de-duplicate rows across pages (entries shift between page requests on a live list).
- Page failures map to a **feature-owned** failure type in the `source` lambda; `onRetry()` → `pager.retry()`.
- The screen SHOULD use `PagingScrollTrigger(listState, viewModel::onLastVisibleIndex)` rather than computing
  "near the end" itself.
- The `PagingConfig` SHOULD be provided under a per-screen qualifier so the next paginated screen binds its own.
- Per-row actions SHOULD be concurrent and idempotent: `State.inFlightIds: Set<RowId>` disables only that row;
  `attempts: MutableMap<RowId, RequestId>` keeps the request id across transient failures and drops it on a
  definitive answer (DATA-50/54).
- Row decorations from a second source (e.g. "already added") SHOULD be **derived in the projection**, never
  stored on the row.
- Returning to the caller SHOULD be a plain pop; the caller re-reads on resume (no result needed).

## Pitfalls

- A request per keystroke (no debounce) or a restart on identical normalised queries.
- Submitting the first key after `stateIn` computed its initial value → a flash of an empty "no results" state.
- Duplicate rows when a page boundary shifts.
- A global "busy" flag that blocks all rows while one row's write is in flight.
- Storing derived availability on the row and forgetting to update it.

## Test checklist

- [ ] typing quickly → one page request after the debounce window (virtual time)
- [ ] whitespace-only change → no new request
- [ ] scroll near end → next page requested once; duplicate rows across pages de-duplicated
- [ ] page failure → footer error; retry re-requests the same page
- [ ] two rows added concurrently → both in flight, each disables only itself
- [ ] transient failure on a row → retry uses the same request id; success drops it
- [ ] derived "already added" flag follows the second source without a reload
