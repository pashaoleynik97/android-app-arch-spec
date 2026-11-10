# Paging — the Pager engine (component: paging)

Server-paginated lists go through `Pager<K, T, F>` in `:lib-common` (package `{{ROOT_PACKAGE}}.libcommon.paging`).
Spec: `.arch-spec/spec/specs/recommended/paging.md` (PAGE-*), why not AndroidX Paging:
`.arch-spec/spec/specs/decisions/ADR-0001-own-pager-over-paging-library.md`, screen recipe:
`.arch-spec/spec/specs/patterns/paginated-picker.md`. The UI half (scroll trigger, footer) is in the compose-ui
skill's `paging-ui.md`.

Contents: 1. Types · 2. Engine rules · 3. ViewModel wiring · 4. DI · 5. Testing

---

## 1. Types

```kotlin
data class PagingConfig(pageSize = 30, prefetchDistance = 10, firstPage = 1)   // require()-validated
data class Page<out T>(items: List<T>, page: Int, hasNext: Boolean, totalCount: Int? = null)
sealed interface PageLoadOutcome<out T, out F> { Loaded(page: Page<T>); Failed(failure: F) }
fun interface PageSource<in K, out T, out F> { suspend fun load(key: K, page: Int, pageSize: Int): PageLoadOutcome<T, F> }
data class PagingState<out T, out F>(items, totalCount, isRefreshing, isAppending, endReached,
                                     refreshFailure: F?, appendFailure: F?) { val isEmpty }
class Pager<K : Any, T : Any, F : Any>(scope, config, source, itemKey: ((T) -> Any)? = null) {
    val state: StateFlow<PagingState<T, F>>
    fun submitKey(newKey: K)            // equal key = no-op; otherwise refresh
    fun refresh()                       // re-load page 1 of the current key; no-op before the first key
    fun onLastVisibleIndex(index: Int)  // the only scroll input
    fun retry()                         // retries whichever load failed
}
```

## 2. Engine rules

**PAGE-01** — One `Pager` per list on `viewModelScope`; `submitKey(initialKey)` before `uiState`'s eager initial
value. *Why:* state is confined to the scope's dispatcher; a late first key flashes an empty "no results" state.
<!-- origin: arch-spec PAGE-01 -->

**PAGE-02** — Single flight; the only scroll input is `onLastVisibleIndex`, fed by `PagingScrollTrigger`; the
threshold is `PagingConfig.prefetchDistance`. *Why:* one tested copy of the threshold; a trigger computed in
composition fires on every frame of a fling.
<!-- origin: arch-spec PAGE-02 -->

**PAGE-03** — Key change / refresh bumps a generation and cancels the running load; stale responses are dropped.
Keys have value equality; normalise (trim, blank → `null`) and debounce search keys in the VM. *Why:* cancellation
alone lets a response already being applied paint page 2 of "ab" under "abc".
<!-- origin: arch-spec PAGE-03 -->

**PAGE-04** — Refresh clears items, counter and failures first. `refreshFailure` → the screen's full-body failure;
`appendFailure` → keep rows, footer with retry, no further appends until `retry()`. *Why:* stale rows under a new key
mislead; an append that re-fires on every scroll nudge hammers a dead network.
<!-- origin: arch-spec PAGE-04 -->

**PAGE-05** — `F` is the feature's own sealed failure, mapped inside the `PageSource` lambda from the classified
service result — never an HTTP type. *Why:* errors are classified once in the data layer.
<!-- origin: arch-spec PAGE-05 -->

**PAGE-06** — Pass `itemKey = { it.id }` for lists that can shift between requests. *Why:* offset paging over a live
list repeats rows; duplicate keys also crash a keyed `LazyColumn`.
<!-- origin: arch-spec PAGE-06 -->

**PAGE-08** — `hasNext` from the server's pagination block; next page derived from the *requested* page; an empty
page ends the list. *Why:* inferring from `items.size` is wrong for a full last page; a buggy echo or `hasNext` must
not make the pager skip or loop.
<!-- origin: arch-spec PAGE-08 -->

**PAGE-09** — Row decorations from a second source are derived in `toUxState`, never stored on paged items. *Why:*
stored decorations go stale when the second source changes.
<!-- origin: arch-spec PAGE-09 -->

**PAGE-10** — A `PageSource` may have side effects; the pager ignores a superseded *result* but the call may have
happened. Guard key- or page-dependent side effects in the source. *Why:* cancellation is cooperative.
<!-- origin: arch-spec PAGE-10 -->

## 3. ViewModel wiring

```kotlin
@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val interactor: OrdersInteractor,
    @param:OrdersPaging private val pagingConfig: PagingConfig,
) : BaseViewModel(), OrdersViewModelContract {

    private data class State(val query: String = "") : ViewModelState
    private val mState = MutableStateFlow(State())

    private val pager = Pager<SearchKey, Order, OrdersFailure>(
        scope = viewModelScope,
        config = pagingConfig,
        source = { key, page, size ->
            when (val r = interactor.loadPage(key.query, page, size)) {
                is OrdersPageResult.Success -> PageLoadOutcome.Loaded(r.page)
                is OrdersPageResult.Failure -> PageLoadOutcome.Failed(r.reason)   // feature-owned
            }
        },
        itemKey = { it.id },
    ).also { it.submitKey(SearchKey(null)) }            // before uiState's initial value (PAGE-01)

    override val uiState = combine(mState, pager.state) { s, p -> s.toUxState(p) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, mState.value.toUxState(pager.state.value))

    init {
        viewModelScope.launch {
            mState.map { SearchKey(it.query.trim().ifEmpty { null }) }
                .distinctUntilChanged()
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .collect(pager::submitKey)
        }
    }

    override fun onQueryChange(value: String) = mState.upd { copy(query = value) }
    override fun onLastVisibleIndex(index: Int) = pager.onLastVisibleIndex(index)
    override fun onRetry() = pager.retry()
    override fun onRefresh() = pager.refresh()
}
```

`toUxState(paging)` maps: `isRefreshing && isEmpty` → loading body; `refreshFailure != null` → failure body with
retry; `isEmpty && endReached` → empty state; otherwise the rows + `paging` for the footer. Re-read on resume
(UI-13) is `pager.refresh()`.

## 4. DI

**PAGE-07** — `PagingConfig` is provided under a per-screen qualifier in the feature's domain module, with
`firstPage` matching the backend. *Why:* each screen tunes its own paging; an off-by-one first page silently skips or
repeats a page.
<!-- origin: arch-spec PAGE-07 -->

```kotlin
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class OrdersPaging

@Provides @Singleton @OrdersPaging
fun provideOrdersPaging(): PagingConfig = PagingConfig(pageSize = 30, prefetchDistance = 10)
```

## 5. Testing

**PAGE-13** — Test with a hand-answered fake source (each load awaits a `CompletableDeferred`) and the pager on its
own `CoroutineScope(StandardTestDispatcher(testScheduler))`, cancelled in `finally`. *Why:* in-flight behaviour is
where the rules live; answering by hand makes it deterministic.
<!-- origin: arch-spec PAGE-13 -->

The engine's own suite is `lib-common/src/test/…/paging/PagerTest.kt` + `FakePageSource.kt` — copy the
`pagerTest { source, pager -> }` harness for a VM test. A VM test checks: debounced key → one request (virtual time),
whitespace-only change → none, threshold → exactly one append, append failure → footer + retry re-requests the same
page, duplicate rows dropped, refresh failure → failure body.
