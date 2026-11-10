package {{ROOT_PACKAGE}}.network.log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpLogSettingsRepositoryTest {

    /** In-memory [HttpLogSettingsStore]: records what was written. */
    private class FakeHttpLogSettingsStore(var stored: HttpLogPolicy) : HttpLogSettingsStore {
        override suspend fun read(): HttpLogPolicy = stored
        override suspend fun write(policy: HttpLogPolicy) {
            stored = policy
        }
    }

    @Test
    fun `the policy reads as the defaults until the stored value has been read`() = runTest {
        val stored = HttpLogPolicy(printFullBodies = true, limit = HttpLogLimit.MB_2)
        val repository = HttpLogSettingsRepository(
            FakeHttpLogSettingsStore(stored),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )

        assertEquals("off / 512 KB is the honest pre-read value", HttpLogPolicy(), repository.policy.value)

        advanceUntilIdle()
        assertEquals(stored, repository.policy.value)
    }

    @Test
    fun `turning full bodies on publishes and persists it, keeping the limit`() = runTest {
        val store = FakeHttpLogSettingsStore(HttpLogPolicy(limit = HttpLogLimit.MB_1))
        val repository = HttpLogSettingsRepository(store, CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        repository.setPrintFullBodies(true)

        val expected = HttpLogPolicy(printFullBodies = true, limit = HttpLogLimit.MB_1)
        assertEquals(expected, repository.policy.value)
        assertEquals(expected, store.stored)
    }

    @Test
    fun `choosing a limit publishes and persists it, keeping the switch`() = runTest {
        val store = FakeHttpLogSettingsStore(HttpLogPolicy(printFullBodies = true))
        val repository = HttpLogSettingsRepository(store, CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        repository.setLimit(HttpLogLimit.MB_2)

        val expected = HttpLogPolicy(printFullBodies = true, limit = HttpLogLimit.MB_2)
        assertEquals(expected, repository.policy.value)
        assertEquals(expected, store.stored)
    }
}
