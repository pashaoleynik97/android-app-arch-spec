package {{ROOT_PACKAGE}}.connectivity.banner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import {{ROOT_PACKAGE}}.connectivity.NetworkState
import {{ROOT_PACKAGE}}.connectivity.NetworkState.OFFLINE
import {{ROOT_PACKAGE}}.connectivity.NetworkState.ONLINE
import {{ROOT_PACKAGE}}.connectivity.NetworkState.SERVER_UNREACHABLE
import {{ROOT_PACKAGE}}.connectivity.NetworkState.UNKNOWN
import {{ROOT_PACKAGE}}.connectivity.support.FakeNetworkStateWatch

class ConnectivityBannerTest {

    // region policy (table)

    @Test
    fun `the policy table`() {
        val offline = BannerAction.Show(ConnectivityBanner.Offline)
        val restored = BannerAction.Show(ConnectivityBanner.Restored)
        val table: List<Triple<Boolean, NetworkState, BannerAction>> = listOf(
            Triple(false, OFFLINE, offline),
            Triple(false, SERVER_UNREACHABLE, offline),
            Triple(false, ONLINE, BannerAction.Keep),
            Triple(false, UNKNOWN, BannerAction.Keep),
            Triple(true, OFFLINE, BannerAction.Keep),
            Triple(true, SERVER_UNREACHABLE, BannerAction.Keep),
            Triple(true, ONLINE, restored),
            Triple(true, UNKNOWN, BannerAction.Clear),
        )
        table.forEach { (wasProblem, state, expected) ->
            assertEquals("$wasProblem → $state", expected, ConnectivityBannerPolicy.onStateChange(wasProblem, state))
        }
    }

    // endregion

    // region controller

    private val watch = FakeNetworkStateWatch()

    private fun TestScope.controller() =
        ConnectivityBannerController(watch, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))).also { runCurrent() }

    @Test
    fun `losing and regaining the server shows offline, then restored`() = runTest {
        val controller = controller()

        watch.networkState.value = OFFLINE
        runCurrent()
        assertEquals(ConnectivityBanner.Offline, controller.banner.value)

        watch.networkState.value = ONLINE
        runCurrent()
        assertEquals(ConnectivityBanner.Restored, controller.banner.value)
    }

    @Test
    fun `a closed offline banner stays closed while the problem lasts`() = runTest {
        val controller = controller()
        watch.networkState.value = OFFLINE
        runCurrent()

        controller.onDismiss()
        watch.networkState.value = SERVER_UNREACHABLE
        runCurrent()

        assertNull(controller.banner.value)
    }

    @Test
    fun `retry re-probes once and spins for the minimum time`() = runTest {
        val controller = controller()

        controller.onRetry()
        controller.onRetry()

        assertEquals("a second tap while spinning is ignored", 1, watch.flushes)
        assertTrue(controller.retrying.value)
        advanceTimeBy(ConnectivityBannerController.MIN_RETRY_SPIN_MILLIS + 1)
        runCurrent()
        assertFalse(controller.retrying.value)
    }

    // endregion
}
