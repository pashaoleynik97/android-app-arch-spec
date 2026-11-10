# Pattern — app-wide cached list

## Problem

A list (the user's open items, inbox, assignments) is the app's home and is shown or consulted from several
places. It must open with data already in hand, stay reasonably fresh without a push channel, never show data
that belongs to another user or context, and never keep an idle session alive by polling. Opening an item from
the list must re-check it, because the list may be minutes old.

## Shape

```
:list (domain)
  interface ItemListCache {
      val snapshot: StateFlow<ListSnapshot>
      suspend fun forceRefresh(): RefreshOutcome            // single-flight, user-driven
  }
  data class ListSnapshot(val list: ItemList? = null, val updatedAtMillis: Long? = null,
                          val isStale: Boolean = true, val isRefreshing: Boolean = false,
                          val lastFailure: ListFailure? = null)
  enum class ListFailure { Connection, AccessDenied, Refused }
  sealed interface RefreshOutcome { Refreshed; Failed(failure); Skipped }
  data class ItemListCacheConfig(val refreshInterval: Duration, val timeToLive: Duration)
  @Singleton class DefaultItemListCache @Inject constructor(service, sessionInfo, foreground: AppForegroundWatch,
      clock: TimeSource, config, @ItemListScope scope: CoroutineScope)
  ItemListInteractor  — exposes cache.snapshot, forceRefresh(), openItem(id): OpenOutcome (the tap-time re-check)
:list-ui
  ItemListViewModel   — owns NO data; uiState = combine(interactor.snapshot, mState) { … }
```

## Rules

- The cache SHOULD be a `@Singleton` with its **own** `CoroutineScope(SupervisorJob() + Dispatchers.Default)`
  (qualified, injected — DATA-61) and be constructed at process start by the host (DATA-62).
- One driver coroutine SHOULD watch the identity the list belongs to (`combine(session, context)` →
  `distinctUntilChanged()` → `collectLatest`). On any change it **clears first**, then starts polling only if there
  is an identity. Inside, `foreground.isForeground.collectLatest { if (it) poll() }` — polling runs only while the
  app is visible, and returning to the foreground refreshes immediately.
- Background ticks SHOULD use the "may not refresh credentials" policy (DATA-19); a tick that meets a dead
  credential remembers it and skips locally until the credential changes. User-driven refreshes may refresh.
- A scope-level refusal (access to the whole context lost) SHOULD pause polling until an explicit refresh or an
  identity change; a request-level refusal SHOULD keep polling (rights restored by an administrator recover on
  their own).
- Staleness SHOULD be a state, flipped by a scheduled job after `timeToLive` (time passing emits nothing — DATA-44);
  a failed refresh keeps the last list. Stale data is not shown as current: the screen waits for a fresh load.
- `forceRefresh` SHOULD be single-flight over a `Deferred` launched on the cache scope; a background tick may ride
  on a user-driven request but never the reverse (DATA-45).
- The ViewModel SHOULD be a **projection**: it holds only screen flags (`isPullRefreshing`, `notice`,
  `openingId`) and combines them with the snapshot. Only a user-initiated pull shows the spinner and failure banner;
  background failures stay silent while data is usable.
- Item open SHOULD be a **tap-time re-check** with **last-tap-wins**: re-tap of the same id is ignored; a tap on
  another id cancels the previous job; a generation counter drops late results; `onScreenInactive()` cancels the
  check. An item found gone SHOULD be removed via `forceRefresh()` and announced with a one-slot notice.
- Per-type destinations SHOULD come from host seams (`(id) -> String` per item type); a `null` seam falls back to
  a stub destination so a card is never dead.

## Pitfalls

- Polling from the screen: the list is cold when the user arrives and polling stops when they leave.
- Not clearing on identity change: the next user briefly sees the previous user's list.
- Background polls with credential refresh: an idle device stays signed in forever.
- Treating "loaded but old" as an error, or blanking the list on a transient failure.
- A user refresh joining a background request that is not allowed to refresh a lapsed credential — the user's
  pull fails for no reason.

## Test checklist

- [ ] warm cache → screen renders at once, no loader
- [ ] identity change → snapshot cleared before the next request
- [ ] background → no requests over several intervals (absence, TEST-41); foreground → immediate refresh
- [ ] background tick uses the non-refreshing policy; user pull uses the refreshing one (contrast pair, TEST-40)
- [ ] 5 concurrent `forceRefresh()` → 1 request, all get the same outcome
- [ ] TTL elapses without success → `isStale = true` emitted
- [ ] scope-level refusal pauses polling; request-level refusal does not
- [ ] failed refresh keeps the previous list
- [ ] last-tap-wins: two taps, out-of-order completion → only the second navigates; first cancelled
- [ ] item gone on open → notice + forced refresh, no navigation
- [ ] long-running poll tests run on `backgroundScope` with `runTest(timeout = …)` (TEST-22)
