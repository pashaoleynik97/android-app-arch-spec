package {{ROOT_PACKAGE}}.libcommon.paging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The paging engine's behaviour, invariant by invariant (see [Pager]'s KDoc for the numbered rules).
 *
 * Loads are answered **by hand**, so "what happens while a request is still in flight" — where the
 * single-flight and generation rules actually earn their keep — is expressible rather than a matter of
 * timing luck. The pager gets its own scope over the test's scheduler so a test may legitimately end
 * with a load still open; [pagerTest] cancels it afterwards.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PagerTest {

    private val defaultConfig = PagingConfig(pageSize = 3, prefetchDistance = 1, firstPage = 1)

    private fun pagerTest(
        config: PagingConfig = defaultConfig,
        itemKey: ((TestItem) -> Any)? = { it.id },
        body: suspend TestScope.(FakePageSource, Pager<TestKey, TestItem, String>) -> Unit,
    ) = runTest(StandardTestDispatcher()) {
        val source = FakePageSource()
        val pagerScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val pager = Pager(scope = pagerScope, config = config, source = source, itemKey = itemKey)
        try {
            body(source, pager)
        } finally {
            pagerScope.cancel()
        }
    }

    // region loading the first page

    @Test
    fun `nothing loads before a key is submitted`() = pagerTest { source, pager ->
        pager.refresh()
        pager.onLastVisibleIndex(0)
        advanceUntilIdle()

        assertTrue(source.calls.isEmpty())
    }

    @Test
    fun `submitting a key loads its first page with the configured size`() = pagerTest { source, pager ->
        pager.submitKey(TestKey(null))
        advanceUntilIdle()
        assertTrue(pager.state.value.isRefreshing)

        source.respondWith("a", "b", hasNext = false, totalCount = 2)
        advanceUntilIdle()

        assertEquals(listOf(FakePageSource.Call(TestKey(null), 1, 3)), source.calls)
        with(pager.state.value) {
            assertEquals(listOf(TestItem("a"), TestItem("b")), items)
            assertEquals(2, totalCount)
            assertFalse(isRefreshing)
            assertTrue(endReached)
        }
    }

    @Test
    fun `an equal key is a no-op`() = pagerTest { source, pager ->
        pager.submitKey(TestKey("abc"))
        advanceUntilIdle()
        source.respondWith("a")
        advanceUntilIdle()

        pager.submitKey(TestKey("abc"))
        advanceUntilIdle()

        assertEquals(1, source.calls.size)
    }

    @Test
    fun `a first-page failure is a refresh failure and leaves the list empty`() = pagerTest { source, pager ->
        pager.submitKey(TestKey(null))
        advanceUntilIdle()
        source.respondFailure("boom")
        advanceUntilIdle()

        with(pager.state.value) {
            assertEquals("boom", refreshFailure)
            assertNull(appendFailure)
            assertFalse(isRefreshing)
            assertTrue(items.isEmpty())
        }
    }

    // endregion

    // region appending

    @Test
    fun `crossing the threshold appends exactly one page`() = pagerTest { source, pager ->
        pager.submitKey(TestKey(null))
        advanceUntilIdle()
        source.respondWith("a", "b", "c", hasNext = true)
        advanceUntilIdle()

        // size 3, prefetchDistance 1 -> the trigger index is 2.
        pager.onLastVisibleIndex(1)
        advanceUntilIdle()
        assertEquals(1, source.calls.size)

        pager.onLastVisibleIndex(2)
        advanceUntilIdle()
        assertEquals(2, source.calls.size)
        assertEquals(2, source.calls.last().page)
        assertTrue(pager.state.value.isAppending)

        // A second trigger while the append is in flight must not start another one.
        pager.onLastVisibleIndex(2)
        advanceUntilIdle()
        assertEquals(2, source.calls.size)

        source.respondWith("d", "e", hasNext = false)
        advanceUntilIdle()

        with(pager.state.value) {
            assertEquals(listOf("a", "b", "c", "d", "e"), items.map { it.id })
            assertFalse(isAppending)
            assertTrue(endReached)
        }
    }

    @Test
    fun `no append once the end is reached`() = pagerTest { source, pager ->
        pager.submitKey(TestKey(null))
        advanceUntilIdle()
        source.respondWith("a", "b", "c", hasNext = false)
        advanceUntilIdle()

        pager.onLastVisibleIndex(2)
        advanceUntilIdle()

        assertEquals(1, source.calls.size)
    }

    @Test
    fun `an empty page ends the list even when the server claims there is more`() = pagerTest { source, pager ->
        pager.submitKey(TestKey(null))
        advanceUntilIdle()
        source.respondWith(hasNext = true)
        advanceUntilIdle()

        assertTrue(pager.state.value.endReached)
        pager.onLastVisibleIndex(0)
        advanceUntilIdle()
        assertEquals(1, source.calls.size)
    }

    @Test
    fun `a failed append keeps the loaded items and blocks further appends until retried`() =
        pagerTest { source, pager ->
            pager.submitKey(TestKey(null))
            advanceUntilIdle()
            source.respondWith("a", "b", "c", hasNext = true)
            advanceUntilIdle()

            pager.onLastVisibleIndex(2)
            advanceUntilIdle()
            source.respondFailure("offline")
            advanceUntilIdle()

            with(pager.state.value) {
                assertEquals("offline", appendFailure)
                assertNull(refreshFailure)
                assertEquals(3, items.size)
            }

            // Jiggling the list must not re-fire the request.
            repeat(5) { pager.onLastVisibleIndex(2) }
            advanceUntilIdle()
            assertEquals(2, source.calls.size)

            pager.retry()
            advanceUntilIdle()
            assertEquals(3, source.calls.size)
            assertEquals(2, source.calls.last().page)
            assertNull(pager.state.value.appendFailure)

            source.respondWith("d", hasNext = false)
            advanceUntilIdle()
            assertEquals(listOf("a", "b", "c", "d"), pager.state.value.items.map { it.id })
        }

    @Test
    fun `retry re-runs the first page when that is what failed`() = pagerTest { source, pager ->
        pager.submitKey(TestKey("xyz"))
        advanceUntilIdle()
        source.respondFailure("boom")
        advanceUntilIdle()

        pager.retry()
        advanceUntilIdle()

        assertEquals(2, source.calls.size)
        assertEquals(FakePageSource.Call(TestKey("xyz"), 1, 3), source.calls.last())
        assertTrue(pager.state.value.isRefreshing)
    }

    @Test
    fun `retry does nothing when nothing failed`() = pagerTest { source, pager ->
        pager.submitKey(TestKey(null))
        advanceUntilIdle()
        source.respondWith("a")
        advanceUntilIdle()

        pager.retry()
        advanceUntilIdle()

        assertEquals(1, source.calls.size)
    }

    // endregion

    // region the generation guard — live search

    @Test
    fun `a page arriving after the key changed is dropped`() = pagerTest { source, pager ->
        pager.submitKey(TestKey("ab"))
        advanceUntilIdle()

        // The user keeps typing before the first answer lands.
        pager.submitKey(TestKey("abc"))
        advanceUntilIdle()

        // Answer the *stale* request first, then the live one.
        source.respondWith("stale-1", "stale-2")
        advanceUntilIdle()
        assertTrue("a superseded page must not land", pager.state.value.items.isEmpty())

        source.respondWith("fresh-1")
        advanceUntilIdle()
        assertEquals(listOf("fresh-1"), pager.state.value.items.map { it.id })
    }

    @Test
    fun `a page-2 response cannot append onto a newer key's results`() = pagerTest { source, pager ->
        pager.submitKey(TestKey("ab"))
        advanceUntilIdle()
        source.respondWith("a", "b", "c", hasNext = true)
        advanceUntilIdle()

        pager.onLastVisibleIndex(2) // append for "ab" goes out
        advanceUntilIdle()
        pager.submitKey(TestKey("abc")) // …and is superseded mid-flight
        advanceUntilIdle()

        source.respondWith("old-page-2")
        advanceUntilIdle()
        assertTrue(pager.state.value.items.isEmpty())

        source.respondWith("new-a")
        advanceUntilIdle()
        assertEquals(listOf("new-a"), pager.state.value.items.map { it.id })
    }

    @Test
    fun `a stale failure cannot mark the new key as failed`() = pagerTest { source, pager ->
        pager.submitKey(TestKey("ab"))
        advanceUntilIdle()
        pager.submitKey(TestKey("abc"))
        advanceUntilIdle()

        source.respondFailure("stale boom")
        advanceUntilIdle()

        assertNull(pager.state.value.refreshFailure)
        assertTrue(pager.state.value.isRefreshing)
    }

    @Test
    fun `a refresh clears items, counter and failures before loading`() = pagerTest { source, pager ->
        pager.submitKey(TestKey(null))
        advanceUntilIdle()
        source.respondWith("a", "b", hasNext = true, totalCount = 40)
        advanceUntilIdle()

        pager.submitKey(TestKey("xyz"))
        advanceUntilIdle()

        with(pager.state.value) {
            assertTrue(items.isEmpty())
            assertNull(totalCount)
            assertNull(refreshFailure)
            assertNull(appendFailure)
            assertTrue(isRefreshing)
            assertFalse(endReached)
        }
    }

    @Test
    fun `an append cannot start while the first page is still loading`() = pagerTest { source, pager ->
        pager.submitKey(TestKey(null))
        advanceUntilIdle()

        pager.onLastVisibleIndex(99)
        advanceUntilIdle()

        assertEquals(1, source.calls.size)
    }

    /**
     * The [PageSource] contract a source with side effects depends on: the pager promises to ignore a superseded
     * *result*, not to refrain from having called the source. Both loads run; only the live one lands.
     */
    @Test
    fun `a superseded load may still have run, but its result is ignored`() = pagerTest { source, pager ->
        pager.submitKey(TestKey("a"))
        advanceUntilIdle()
        pager.submitKey(TestKey("ab"))
        advanceUntilIdle()

        assertEquals(listOf(TestKey("a"), TestKey("ab")), source.calls.map { it.key })

        source.respondWith("stale")
        advanceUntilIdle()
        assertTrue(pager.state.value.items.isEmpty())
    }

    // endregion

    // region de-duplication and the counter

    @Test
    fun `an appended page drops rows already loaded`() = pagerTest { source, pager ->
        pager.submitKey(TestKey(null))
        advanceUntilIdle()
        source.respondWith("a", "b", "c", hasNext = true)
        advanceUntilIdle()

        pager.onLastVisibleIndex(2)
        advanceUntilIdle()
        // The server's ordering shifted under a concurrent insert: "c" comes back a second time.
        source.respondWith("c", "d", "d", hasNext = false)
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c", "d"), pager.state.value.items.map { it.id })
    }

    @Test
    fun `without an item key duplicates are kept`() = pagerTest(itemKey = null) { source, pager ->
        pager.submitKey(TestKey(null))
        advanceUntilIdle()
        source.respondWith("a", "b", "c", hasNext = true)
        advanceUntilIdle()
        pager.onLastVisibleIndex(2)
        advanceUntilIdle()
        source.respondWith("c", hasNext = false)
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c", "c"), pager.state.value.items.map { it.id })
    }

    @Test
    fun `the counter follows the latest page so a drifting total self-corrects`() = pagerTest { source, pager ->
        pager.submitKey(TestKey(null))
        advanceUntilIdle()
        source.respondWith("a", "b", "c", hasNext = true, totalCount = 40)
        advanceUntilIdle()
        assertEquals(40, pager.state.value.totalCount)

        pager.onLastVisibleIndex(2)
        advanceUntilIdle()
        source.respondWith("d", hasNext = false, totalCount = 41)
        advanceUntilIdle()

        assertEquals(41, pager.state.value.totalCount)
    }

    @Test
    fun `the next page is derived from the requested page, not the echoed one`() = pagerTest { source, pager ->
        pager.submitKey(TestKey(null))
        advanceUntilIdle()
        // A server that echoes a nonsense page number must not be able to make the pager skip one.
        source.respond(FakePageSource.page("a", "b", "c", hasNext = true, pageNumber = 77))
        advanceUntilIdle()

        pager.onLastVisibleIndex(2)
        advanceUntilIdle()

        assertEquals(2, source.calls.last().page)
    }

    // endregion
}
