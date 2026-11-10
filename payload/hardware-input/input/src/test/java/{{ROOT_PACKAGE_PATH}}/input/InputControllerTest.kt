package {{ROOT_PACKAGE}}.input

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import {{ROOT_PACKAGE}}.input.api.InputDriverId
import {{ROOT_PACKAGE}}.input.settings.InputSettingsRepository
import {{ROOT_PACKAGE}}.input.settings.InputSettingsStore

/**
 * The controller's contract: claim while resumed, release on pause, swap cleanly when the preference changes with
 * the screen still in front, and deliver each reading once, to every current collector, never to a late one.
 */
class InputControllerTest {

    private val vendor = InputDriverId("vendor")
    private val debug = InputDriverId("debug")

    /** The controller never touches the lifecycle itself; it only receives the callbacks. */
    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = error("not used by InputController")
    }

    private class Fixture(scope: TestScope, providers: List<FakeInputDriverProvider>, stored: InputPreference) {
        val store = FakeInputSettingsStore(stored)
        val settings = InputSettingsRepository(store, scope)
        val controller = InputController(InputDriverResolver(providers.toSet()), settings, scope)
    }

    @Test
    fun `no driver is created before the stored preference has been read`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val provider = FakeInputDriverProvider(vendor)
        val fixture = Fixture(scope, listOf(provider), InputPreference.Disabled)

        assertTrue(provider.created.isEmpty())
        assertNull(fixture.controller.activeDriverId.value)

        scope.testScheduler.advanceUntilIdle()
        assertTrue("the stored Disabled is honoured, not a guessed Auto", provider.created.isEmpty())
    }

    @Test
    fun `input is not unavailable before the stored preference has been read`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val read = CompletableDeferred<Unit>()
        val store = object : InputSettingsStore {
            override suspend fun read(): InputPreference {
                read.await()
                return InputPreference.Auto
            }

            override suspend fun write(preference: InputPreference) = Unit
        }
        val controller = InputController(InputDriverResolver(emptySet()), InputSettingsRepository(store, scope), scope)

        scope.testScheduler.runCurrent()
        assertFalse("not known yet is not unavailable", controller.isUnavailable.value)

        read.complete(Unit)
        scope.testScheduler.advanceUntilIdle()
        assertTrue(controller.isUnavailable.value)
    }

    @Test
    fun `disabling the driver or a failing driver makes input unavailable`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val provider = FakeInputDriverProvider(vendor)
        val fixture = Fixture(scope, listOf(provider), InputPreference.Auto)
        scope.testScheduler.advanceUntilIdle()
        assertFalse(fixture.controller.isUnavailable.value)

        provider.created.single().fail(IllegalStateException("claim refused"))
        scope.testScheduler.advanceUntilIdle()
        assertTrue(fixture.controller.isUnavailable.value)

        fixture.settings.setPreference(InputPreference.Disabled)
        scope.testScheduler.advanceUntilIdle()
        assertTrue(fixture.controller.isUnavailable.value)
    }

    @Test
    fun `resume activates the driver and pause releases it`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val provider = FakeInputDriverProvider(vendor)
        val fixture = Fixture(scope, listOf(provider), InputPreference.Auto)
        scope.testScheduler.advanceUntilIdle()

        val driver = provider.created.single()
        // A new driver starts "not resumed", so it is deactivated once on creation — harmless, because the contract
        // makes deactivate idempotent; hence the assertions count the change, not the absolute number.
        assertEquals(0, driver.activateCount)
        val deactivationsBeforeResume = driver.deactivateCount

        fixture.controller.onResume(owner)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(1, driver.activateCount)

        fixture.controller.onPause(owner)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(deactivationsBeforeResume + 1, driver.deactivateCount)
        assertEquals("nothing re-claimed it behind the pause", 1, driver.activateCount)
    }

    @Test
    fun `changing the preference while resumed disposes the old driver and activates the new one`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val vendorProvider = FakeInputDriverProvider(vendor, priority = 100)
        val debugProvider = FakeInputDriverProvider(debug, priority = -100)
        val fixture = Fixture(scope, listOf(vendorProvider, debugProvider), InputPreference.Auto)
        scope.testScheduler.advanceUntilIdle()
        fixture.controller.onResume(owner)
        scope.testScheduler.advanceUntilIdle()
        val first = vendorProvider.created.single()
        assertEquals(1, first.activateCount)

        fixture.settings.setPreference(InputPreference.Forced(debug))
        scope.testScheduler.advanceUntilIdle()

        assertEquals(1, first.disposeCount)
        assertEquals(1, debugProvider.created.single().activateCount)
        assertEquals(debug, fixture.controller.activeDriverId.value)
    }

    @Test
    fun `every current collector receives the same event`() = runTest {
        // The observable half of "logged exactly once": the flow is shared, not re-collected per subscriber.
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val provider = FakeInputDriverProvider(vendor)
        val fixture = Fixture(scope, listOf(provider), InputPreference.Auto)
        scope.testScheduler.advanceUntilIdle()
        val driver = provider.created.single()

        fixture.controller.events.test {
            fixture.controller.events.test {
                scope.testScheduler.advanceUntilIdle()
                driver.emit("4006381333931")
                scope.testScheduler.advanceUntilIdle()
                assertEquals("4006381333931", awaitItem().data)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals("4006381333931", awaitItem().data)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an event is never replayed to a collector that subscribes afterwards`() = runTest {
        // A replayed reading would be redelivered to a freshly opened screen and act twice.
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val provider = FakeInputDriverProvider(vendor)
        val fixture = Fixture(scope, listOf(provider), InputPreference.Auto)
        scope.testScheduler.advanceUntilIdle()

        provider.created.single().emit("already-gone")
        scope.testScheduler.advanceUntilIdle()

        fixture.controller.events.test {
            scope.testScheduler.advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
