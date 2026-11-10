package {{ROOT_PACKAGE}}.input

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import {{ROOT_PACKAGE}}.input.api.InputDriverId
import {{ROOT_PACKAGE}}.input.settings.InputSettingsRepository

class InputSettingsRepositoryTest {

    @Test
    fun `preference is null until the stored value has been read`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val repository = InputSettingsRepository(FakeInputSettingsStore(InputPreference.Disabled), scope)

        assertNull("the controller must not act on a guessed value", repository.preference.value)

        scope.testScheduler.advanceUntilIdle()
        assertEquals(InputPreference.Disabled, repository.preference.value)
    }

    @Test
    fun `setting a preference publishes it at once and persists it`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val store = FakeInputSettingsStore()
        val repository = InputSettingsRepository(store, scope)
        scope.testScheduler.advanceUntilIdle()

        val forced = InputPreference.Forced(InputDriverId("vendor"))
        repository.setPreference(forced)
        assertEquals(forced, repository.preference.value)

        scope.testScheduler.advanceUntilIdle()
        assertEquals(forced, store.stored)
    }

    @Test
    fun `a choice made before the read completes is not overwritten by the stale stored value`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val repository = InputSettingsRepository(FakeInputSettingsStore(InputPreference.Auto), scope)

        repository.setPreference(InputPreference.Disabled)
        scope.testScheduler.advanceUntilIdle()

        assertEquals(InputPreference.Disabled, repository.preference.value)
    }
}
