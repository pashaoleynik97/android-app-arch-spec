package {{ROOT_PACKAGE}}.connectivity.internal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import {{ROOT_PACKAGE}}.connectivity.NetworkState
import {{ROOT_PACKAGE}}.connectivity.support.FakeConnectivityMonitor
import {{ROOT_PACKAGE}}.connectivity.support.FakeServerHealthProbe

class DefaultNetworkStateWatchTest {

    private val monitor = FakeConnectivityMonitor()
    private val probe = FakeServerHealthProbe()

    private fun TestScope.watch() =
        DefaultNetworkStateWatch(monitor, probe, CoroutineScope(StandardTestDispatcher(testScheduler))).also { advanceUntilIdle() }

    @Test
    fun `starts UNKNOWN`() = runTest {
        assertEquals(NetworkState.UNKNOWN, watch().getCurrentNetworkState())
    }

    @Test
    fun `a link with a healthy server is ONLINE, a failing probe SERVER_UNREACHABLE`() = runTest {
        val watch = watch()

        monitor.set(LinkStatus.UP)
        advanceUntilIdle()
        assertEquals(NetworkState.ONLINE, watch.networkState.value)

        probe.reachable = false
        monitor.set(LinkStatus.UP)
        advanceUntilIdle()
        assertEquals(NetworkState.SERVER_UNREACHABLE, watch.getCurrentNetworkState())
    }

    @Test
    fun `no link is OFFLINE at once, without a probe`() = runTest {
        val watch = watch()

        monitor.set(LinkStatus.NONE)
        advanceUntilIdle()

        assertEquals(NetworkState.OFFLINE, watch.getCurrentNetworkState())
        assertEquals(0, probe.calls)
    }

    @Test
    fun `flush re-probes and keeps the current value until the fresh answer lands`() = runTest {
        val watch = watch()
        monitor.set(LinkStatus.UP)
        advanceUntilIdle()
        assertEquals(1, probe.calls)

        probe.reachable = false
        probe.gate = CompletableDeferred()
        watch.flush()
        advanceUntilIdle()
        assertEquals(2, probe.calls)
        assertEquals("no flicker while the probe runs", NetworkState.ONLINE, watch.getCurrentNetworkState())

        probe.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(NetworkState.SERVER_UNREACHABLE, watch.getCurrentNetworkState())
    }

    @Test
    fun `a burst of flushes costs one probe`() = runTest {
        val watch = watch()
        monitor.set(LinkStatus.UP)
        advanceUntilIdle()

        repeat(5) { watch.flush() }
        advanceUntilIdle()

        assertEquals(2, probe.calls)
    }

    @Test
    fun `a newer trigger cancels the probe in flight`() = runTest {
        val watch = watch()
        probe.gate = CompletableDeferred() // the first probe hangs
        monitor.set(LinkStatus.UP)
        advanceUntilIdle()

        probe.gate = null
        monitor.set(LinkStatus.NONE)
        advanceUntilIdle()

        assertEquals("the stale probe never lands", NetworkState.OFFLINE, watch.getCurrentNetworkState())
    }
}
