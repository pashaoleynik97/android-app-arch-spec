package {{ROOT_PACKAGE}}.libcommon.paging

/**
 * How a paginated list is fetched and when the next page is asked for.
 *
 * A plain data class rather than constants so every call site can tune it, and so a feature can provide its
 * own instance through DI under a per-screen qualifier (`@Provides @OrdersPaging fun …(): PagingConfig`).
 *
 * @param pageSize how many items one request asks for.
 * @param prefetchDistance how close to the end of the loaded list the user must scroll before the next page is
 *   requested, in items. `10` means "start loading when the 10th-from-last row becomes visible".
 * @param firstPage the number of the first page. `1` because the example contract's list endpoints are
 *   1-based; a 0-based backend sets `firstPage = 0` here, once, instead of shifting numbers in every source.
 */
data class PagingConfig(
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val prefetchDistance: Int = DEFAULT_PREFETCH_DISTANCE,
    val firstPage: Int = DEFAULT_FIRST_PAGE,
) {
    init {
        require(pageSize > 0) { "pageSize must be positive, was $pageSize" }
        require(prefetchDistance >= 0) { "prefetchDistance must not be negative, was $prefetchDistance" }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 30
        const val DEFAULT_PREFETCH_DISTANCE = 10
        const val DEFAULT_FIRST_PAGE = 1
    }
}

/**
 * One page, exactly as the server described it.
 *
 * [hasNext] comes from the response's own pagination block rather than being inferred from
 * `items.size == pageSize` — the two disagree whenever the last page happens to be full.
 *
 * [totalCount] is the number of items matching the current key **across all pages** — what an "N results"
 * counter must show; `null` when the endpoint does not report one.
 */
data class Page<out T>(
    val items: List<T>,
    val page: Int,
    val hasNext: Boolean,
    val totalCount: Int? = null,
)

/**
 * The outcome of loading one page.
 *
 * The failure type is the **feature's own**, never an HTTP type: classification happens in the feature's service
 * (DATA rules), and the pager only carries the verdict through to the ViewModel, which maps it to a notice or a
 * footer.
 */
sealed interface PageLoadOutcome<out T, out F> {
    data class Loaded<T>(val page: Page<T>) : PageLoadOutcome<T, Nothing>
    data class Failed<F>(val failure: F) : PageLoadOutcome<Nothing, F>
}

/**
 * Where pages come from.
 *
 * @param K the query key — a search string, a filter object, anything with value equality. Changing it restarts
 *   the list from [PagingConfig.firstPage]; submitting an equal one does nothing.
 *
 * **A source may have side effects.** [Pager] promises only to ignore the *result* of a superseded load, not to
 * refrain from calling it — a load that is cancelled or outdated may already have written something (a cache, a
 * "last seen" marker). That is safe when the side effect depends on neither the key nor the page; if it does,
 * guard it in the source.
 */
fun interface PageSource<in K : Any, out T : Any, out F : Any> {
    suspend fun load(key: K, page: Int, pageSize: Int): PageLoadOutcome<T, F>
}

/**
 * Everything a paginated list renders from.
 *
 * The two failures are separate because they mean different things to the user: [refreshFailure] is "there is no
 * list", which replaces the body; [appendFailure] is "there is more, but it could not be fetched", which is a
 * footer under a list that is still perfectly usable.
 */
data class PagingState<out T, out F>(
    val items: List<T> = emptyList(),
    val totalCount: Int? = null,
    /** The first page of the current key is in flight — the body shows placeholders. */
    val isRefreshing: Boolean = false,
    /** A subsequent page is in flight — the list gets a loading footer. */
    val isAppending: Boolean = false,
    /** The server said there is nothing after what is loaded. No further append will be attempted. */
    val endReached: Boolean = false,
    val refreshFailure: F? = null,
    val appendFailure: F? = null,
) {
    /** `true` while nothing is loaded for the current key (before the first page, or an empty result). */
    val isEmpty: Boolean get() = items.isEmpty()
}
