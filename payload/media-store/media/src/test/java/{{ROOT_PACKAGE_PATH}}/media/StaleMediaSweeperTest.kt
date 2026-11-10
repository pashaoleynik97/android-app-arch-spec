package {{ROOT_PACKAGE}}.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import {{ROOT_PACKAGE}}.coretesting.RecordingReporter

class StaleMediaSweeperTest {

    @get:Rule
    val reporter = RecordingReporter()

    private val store = FakeLocalMediaStore()

    @Test
    fun `start sweeps once per process no matter how often it is called`() = runTest {
        val sweeper = StaleMediaSweeper(store, CoroutineScope(StandardTestDispatcher(testScheduler)))

        sweeper.start()
        sweeper.start()
        advanceUntilIdle()

        assertEquals(1, store.sweepCalls)
    }

    @Test
    fun `nothing is swept before start`() = runTest {
        StaleMediaSweeper(store, CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        assertEquals(0, store.sweepCalls)
    }

    @Test
    fun `a failing sweep is reported, not thrown, and leaves the scope alive`() = runTest {
        store.sweepFailure = IllegalStateException("disk gone")
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val sweeper = StaleMediaSweeper(store, scope)

        sweeper.start()
        advanceUntilIdle()

        assertTrue(reporter.exceptions.any { "stale media sweep failed" in it.message.orEmpty() })
        val deletion = MediaDeletionScheduler(store, scope)
        deletion.scheduleDeletion(listOf("/a"))
        advanceUntilIdle()
        assertEquals("the shared scope still runs work after a failed sweep", listOf(listOf("/a")), store.deleted)
    }
}
