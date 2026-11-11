package {{ROOT_PACKAGE}}.network.access

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAccessLossBusTest {

    private val loss = AccessLoss.ScopeLost(where = "/v1/items", code = "SCOPE_ACCESS_REVOKED")

    /** Records what it was offered; consumes when [consumes]. */
    private open class Recorder(
        private val consumes: Boolean = false,
        private val log: MutableList<String>? = null,
        private val name: String = "",
    ) : AccessLossHandler {
        val seen = mutableListOf<AccessLoss>()
        override suspend fun handle(loss: AccessLoss): Boolean {
            seen += loss
            log?.add(name)
            return consumes
        }
    }

    private class Filter(consumes: Boolean, log: MutableList<String>, name: String) : Recorder(consumes, log, name), AccessLossFilter

    private fun TestScope.bus(filters: Set<AccessLossFilter> = emptySet()) =
        DefaultAccessLossBus(CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), filters)

    @Test
    fun `an unconsumed loss reaches the fallback`() = runTest {
        val bus = bus()
        val screen = Recorder(consumes = false)
        val host = Recorder(consumes = true)
        bus.register(screen)
        bus.setFallback(host)

        bus.report(loss)
        advanceUntilIdle()

        assertEquals(listOf(loss), screen.seen)
        assertEquals(listOf(loss), host.seen)
    }

    @Test
    fun `a consuming handler stops the chain before the host`() = runTest {
        val bus = bus()
        val host = Recorder(consumes = true)
        bus.register(Recorder(consumes = true))
        bus.setFallback(host)

        bus.report(loss)
        advanceUntilIdle()

        assertTrue("the host must not see a loss the active screen handled", host.seen.isEmpty())
    }

    @Test
    fun `order is newest registration first, then standing filters, then the fallback`() = runTest {
        val order = mutableListOf<String>()
        val bus = bus(setOf(Filter(consumes = false, order, "filter")))
        bus.register(Recorder(false, order, "older"))
        bus.register(Recorder(false, order, "newer"))
        bus.setFallback(Recorder(true, order, "host"))

        bus.report(loss)
        advanceUntilIdle()

        assertEquals(listOf("newer", "older", "filter", "host"), order)
    }

    @Test
    fun `a standing filter can swallow a feature's expected 403`() = runTest {
        val order = mutableListOf<String>()
        val bus = bus(setOf(Filter(consumes = true, order, "filter")))
        val host = Recorder(true)
        bus.setFallback(host)

        bus.report(AccessLoss.Forbidden("/v1/items/1", "ITEM_NOT_ALLOWED"))
        advanceUntilIdle()

        assertEquals(listOf("filter"), order)
        assertTrue(host.seen.isEmpty())
    }

    @Test
    fun `a cancelled registration stops receiving losses`() = runTest {
        val bus = bus()
        val screen = Recorder(consumes = true)
        val host = Recorder(consumes = true)
        bus.register(screen).cancel()
        bus.setFallback(host)

        bus.report(loss)
        advanceUntilIdle()

        assertTrue(screen.seen.isEmpty())
        assertEquals(listOf(loss), host.seen)
    }

    @Test
    fun `a throwing handler does not break the chain`() = runTest {
        val bus = bus()
        val host = Recorder(consumes = true)
        bus.register { error("boom") }
        bus.setFallback(host)

        bus.report(loss)
        advanceUntilIdle()

        assertEquals(listOf(loss), host.seen)
    }

    @Test
    fun `reports made before the drain runs are delivered, in order`() = runTest {
        val bus = bus()
        val host = Recorder(consumes = true)
        bus.setFallback(host)
        val second = AccessLoss.Forbidden("/v1/items/2", null)

        bus.report(loss) // the drain coroutine has not run yet: a SharedFlow would drop these
        bus.report(second)
        advanceUntilIdle()

        assertEquals(listOf(loss, second), host.seen)
    }
}
