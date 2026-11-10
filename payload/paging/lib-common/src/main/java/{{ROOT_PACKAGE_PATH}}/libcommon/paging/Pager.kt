package {{ROOT_PACKAGE}}.libcommon.paging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The project's paging engine: a small, explicit state machine over "load page N of key K".
 *
 * Deliberately **not** `androidx.paging` (see the spec's ADR on the own pager): that library owns the item
 * stream, the diffing and the UI adapter, and bends badly to a list whose rows are decorated from a second,
 * independent source. This class owns only the page bookkeeping and hands the ViewModel a plain [PagingState]
 * to project into its own `UxState`, like any other data.
 *
 * **The rules, in full** (each one has a test in `PagerTest`):
 *  1. *Single-flight.* At most one load runs. [onLastVisibleIndex] is inert while refreshing, while appending,
 *     once [PagingState.endReached], and while an [PagingState.appendFailure] is pending — a failed append
 *     waits for an explicit [retry] rather than re-firing every time the user nudges the list against a dead
 *     network.
 *  2. *Generation guard.* [submitKey] and [refresh] bump a counter and cancel the running job; a response
 *     stamped with an older generation is dropped. This is what makes live search safe: a page-2 response for
 *     "ab" can never append onto the results for "abc".
 *  3. *An equal key is a no-op.* Typing `abc` → `ab` → `abc` (after debounce) re-requests nothing new.
 *  4. *A refresh clears everything first* — items, counter and both failures — so the body shows placeholders
 *     and the counter shows nothing rather than a stale number under a new key.
 *  5. *An append failure never drops loaded items.* The list stays; the footer explains itself.
 *  6. *An empty page ends the list* even when the server claims there is more, and the next page number is
 *     derived from the page that was *requested*, never from the one the server echoes.
 *
 * **Threading.** Every public method must be called from [scope]'s dispatcher (`viewModelScope` ⇒
 * `Dispatchers.Main.immediate`). All mutable bookkeeping is confined to it, which is why there is no lock here.
 *
 * @param itemKey identity of an item, used to drop duplicates an append would otherwise introduce when the
 *   server's ordering shifts under a concurrent insert. `null` disables de-duplication.
 */
class Pager<K : Any, T : Any, F : Any>(
    private val scope: CoroutineScope,
    private val config: PagingConfig,
    private val source: PageSource<K, T, F>,
    private val itemKey: ((T) -> Any)? = null,
) {

    private val mState = MutableStateFlow(PagingState<T, F>())
    val state: StateFlow<PagingState<T, F>> = mState.asStateFlow()

    /** The key being paged. `null` until the first [submitKey] — nothing loads before then. */
    private var key: K? = null

    /** Bumped by every reset; a load whose stamp no longer matches has been superseded. */
    private var generation = 0

    private var job: Job? = null

    /** The page a subsequent append would ask for. */
    private var nextPage = config.firstPage

    /**
     * Points the pager at [newKey] and loads its first page. Does nothing when the key is unchanged, which is
     * what lets a live search call this on every debounced keystroke.
     */
    fun submitKey(newKey: K) {
        if (key == newKey) return
        key = newKey
        startRefresh()
    }

    /** Re-loads the current key from its first page (pull-to-refresh, re-read on resume). No-op before the first key. */
    fun refresh() {
        if (key == null) return
        startRefresh()
    }

    /**
     * The UI's only scroll input: the index of the last row the user can currently see. The threshold lives here
     * rather than in the composable so there is exactly one copy of it, and it is unit-tested.
     */
    fun onLastVisibleIndex(index: Int) {
        val current = mState.value
        if (current.isRefreshing || current.isAppending || current.endReached) return
        if (current.appendFailure != null || current.items.isEmpty()) return
        if (index < current.items.size - config.prefetchDistance) return
        startAppend()
    }

    /** Retries whichever load failed — the first page, or the append. Does nothing when nothing failed. */
    fun retry() {
        val current = mState.value
        when {
            current.refreshFailure != null -> startRefresh()
            current.appendFailure != null -> {
                mState.value = current.copy(appendFailure = null)
                startAppend()
            }
            else -> Unit
        }
    }

    private fun startRefresh() {
        val currentKey = key ?: return
        val stamp = ++generation
        job?.cancel()
        nextPage = config.firstPage
        // A whole new list: drop the old items, counter and failures rather than showing one key's results under
        // another key's heading.
        mState.value = PagingState(isRefreshing = true)
        job = scope.launch { load(currentKey, config.firstPage, stamp, isAppend = false) }
    }

    private fun startAppend() {
        val currentKey = key ?: return
        // Deliberately not a new generation: an append supersedes nothing, so a refresh started while it is in
        // flight still wins.
        val stamp = generation
        val page = nextPage
        mState.value = mState.value.copy(isAppending = true)
        job = scope.launch { load(currentKey, page, stamp, isAppend = true) }
    }

    private suspend fun load(currentKey: K, page: Int, stamp: Int, isAppend: Boolean) {
        val outcome = source.load(currentKey, page, config.pageSize)
        // Superseded while in flight: the answer belongs to a list nobody is looking at any more.
        if (stamp != generation) return
        when (outcome) {
            is PageLoadOutcome.Loaded -> applyPage(outcome.page, page, isAppend)
            is PageLoadOutcome.Failed -> applyFailure(outcome.failure, isAppend)
        }
    }

    private fun applyPage(loaded: Page<T>, requestedPage: Int, isAppend: Boolean) {
        val current = mState.value
        val existing = if (isAppend) current.items else emptyList()
        // Requested rather than echoed: a server that reports a page number nobody asked for must not be able to
        // make the pager skip or repeat one.
        nextPage = requestedPage + 1
        mState.value = current.copy(
            items = existing + deduplicate(existing, loaded.items),
            totalCount = loaded.totalCount,
            isRefreshing = false,
            isAppending = false,
            // An empty page ends the list even if the server still claims there is more — otherwise a buggy
            // `hasNext` would spin the pager against the same page forever.
            endReached = !loaded.hasNext || loaded.items.isEmpty(),
            refreshFailure = null,
            appendFailure = null,
        )
    }

    private fun applyFailure(failure: F, isAppend: Boolean) {
        val current = mState.value
        mState.value = if (isAppend) {
            current.copy(isAppending = false, appendFailure = failure)
        } else {
            current.copy(isRefreshing = false, refreshFailure = failure)
        }
    }

    /** Drops rows already loaded, and any repeated within [incoming] itself. */
    private fun deduplicate(existing: List<T>, incoming: List<T>): List<T> {
        val identity = itemKey ?: return incoming
        val seen = existing.mapTo(HashSet(existing.size)) { identity(it) }
        return incoming.filter { seen.add(identity(it)) }
    }
}
