package {{ROOT_PACKAGE}}.libcommon.paging

import kotlinx.coroutines.CompletableDeferred

/** A row with an identity, so de-duplication can be asserted. */
data class TestItem(val id: String)

/** The query key — a value with equality, exactly as a search screen uses it. */
data class TestKey(val query: String?)

/**
 * A [PageSource] whose every load is answered **by hand**, so a test can hold a request open and act on the pager
 * while it is in flight — which is where the single-flight and generation rules live.
 */
class FakePageSource : PageSource<TestKey, TestItem, String> {

    data class Call(val key: TestKey, val page: Int, val pageSize: Int)

    val calls = mutableListOf<Call>()
    private val pending = ArrayDeque<CompletableDeferred<PageLoadOutcome<TestItem, String>>>()

    override suspend fun load(key: TestKey, page: Int, pageSize: Int): PageLoadOutcome<TestItem, String> {
        calls += Call(key, page, pageSize)
        val deferred = CompletableDeferred<PageLoadOutcome<TestItem, String>>()
        pending += deferred
        return deferred.await()
    }

    /** Answers the oldest still-open load. */
    fun respond(outcome: PageLoadOutcome<TestItem, String>) {
        pending.removeFirst().complete(outcome)
    }

    fun respondWith(vararg ids: String, hasNext: Boolean = false, totalCount: Int? = null) {
        respond(page(*ids, hasNext = hasNext, totalCount = totalCount))
    }

    fun respondFailure(failure: String) {
        respond(PageLoadOutcome.Failed(failure))
    }

    companion object {
        fun page(
            vararg ids: String,
            hasNext: Boolean = false,
            totalCount: Int? = null,
            pageNumber: Int = 1,
        ): PageLoadOutcome<TestItem, String> = PageLoadOutcome.Loaded(
            Page(items = ids.map(::TestItem), page = pageNumber, hasNext = hasNext, totalCount = totalCount),
        )
    }
}
