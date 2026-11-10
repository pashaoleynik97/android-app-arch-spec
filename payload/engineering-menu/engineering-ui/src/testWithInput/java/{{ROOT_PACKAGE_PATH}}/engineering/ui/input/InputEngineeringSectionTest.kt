package {{ROOT_PACKAGE}}.engineering.ui.input

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringControl
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringRow
import {{ROOT_PACKAGE}}.input.InputController
import {{ROOT_PACKAGE}}.input.InputDriverResolver
import {{ROOT_PACKAGE}}.input.InputPreference
import {{ROOT_PACKAGE}}.input.api.Detection
import {{ROOT_PACKAGE}}.input.api.InputDriver
import {{ROOT_PACKAGE}}.input.api.InputDriverId
import {{ROOT_PACKAGE}}.input.api.InputDriverProvider
import {{ROOT_PACKAGE}}.input.api.InputDriverState
import {{ROOT_PACKAGE}}.input.api.InputEvent
import {{ROOT_PACKAGE}}.input.settings.InputSettingsRepository
import {{ROOT_PACKAGE}}.input.settings.InputSettingsStore

/** Compiled and run only when the hardware-input component is installed (see the module build file). */
class InputEngineeringSectionTest {

    private class Driver(override val id: InputDriverId) : InputDriver {
        val emitted = MutableSharedFlow<InputEvent>(extraBufferCapacity = 8)
        override val events = emitted
        override val state = MutableStateFlow<InputDriverState>(InputDriverState.Idle).asStateFlow()
        override suspend fun activate() = Unit
        override suspend fun deactivate() = Unit
        override fun dispose() = Unit
    }

    private class Provider(
        override val id: InputDriverId,
        override val priority: Int,
        private val detection: Detection,
    ) : InputDriverProvider {
        val drivers = mutableListOf<Driver>()
        override val displayName = "Driver ${id.value}"
        override fun detect() = detection
        override fun create(): InputDriver = Driver(id).also { drivers += it }
    }

    private class Store(var stored: InputPreference) : InputSettingsStore {
        override suspend fun read() = stored
        override suspend fun write(preference: InputPreference) {
            stored = preference
        }
    }

    private val debug = Provider(InputDriverId("debug"), priority = -100, detection = Detection.Supported("adb"))
    private val vendor = Provider(InputDriverId("vendor"), priority = 100, detection = Detection.Unsupported)

    private fun rowsOf(rows: List<EngineeringRow>) = rows.associateBy { it.key }

    @Test
    fun `shows the preference, the active driver, every detection and the last reading`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val settings = InputSettingsRepository(Store(InputPreference.Auto), scope)
        val controller = InputController(InputDriverResolver(setOf(debug, vendor)), settings, scope)
        val section = InputEngineeringSection(controller, settings)
        var latest: List<EngineeringRow> = emptyList()
        // On the controller's scope (foreground work): advanceUntilIdle does not wait for backgroundScope tasks.
        val collecting = scope.launch { section.rows.collect { latest = it } }
        scope.testScheduler.advanceUntilIdle()

        val rows = rowsOf(latest)
        assertEquals("Auto", rows.getValue("driver").description)
        assertTrue(rows.getValue("active").description.startsWith("debug"))
        assertTrue(rows.getValue("detect-vendor").description.startsWith("Not detected"))
        assertTrue(rows.getValue("detect-debug").description.contains("adb"))
        assertTrue(rows.getValue("last-reading").description.startsWith("Nothing received"))

        debug.drivers.single().emitted.tryEmit(
            InputEvent("4006381333931", null, "ean13", InputDriverId("debug"), 0L, mapOf("injectedBy" to "adb")),
        )
        scope.testScheduler.advanceUntilIdle()

        val reading = rowsOf(latest).getValue("last-reading").description
        assertTrue(reading, reading.startsWith("4006…931 (13 chars) · kind=ean13 · source=debug"))
        assertFalse("the payload is never shown in full", reading.contains("4006381333931"))
        collecting.cancel()
    }

    @Test
    fun `picking a driver applies it through the settings repository`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val store = Store(InputPreference.Auto)
        val settings = InputSettingsRepository(store, scope)
        val controller = InputController(InputDriverResolver(setOf(debug, vendor)), settings, scope)
        val section = InputEngineeringSection(controller, settings)
        var latest: List<EngineeringRow> = emptyList()
        // On the controller's scope (foreground work): advanceUntilIdle does not wait for backgroundScope tasks.
        val collecting = scope.launch { section.rows.collect { latest = it } }
        scope.testScheduler.advanceUntilIdle()

        val picker = rowsOf(latest).getValue("driver").control as EngineeringControl.Picker
        assertEquals(listOf("auto", "forced:vendor", "forced:debug", "disabled"), picker.options.map { it.key })

        picker.onSelect("disabled")
        scope.testScheduler.advanceUntilIdle()

        assertEquals(InputPreference.Disabled, settings.preference.value)
        assertEquals(InputPreference.Disabled, store.stored)
        assertEquals("Disabled", rowsOf(latest).getValue("driver").description)
        assertEquals("None", rowsOf(latest).getValue("active").description)
        collecting.cancel()
    }

    @Test
    fun `short payloads are fully masked`() {
        assertEquals("••• (3 chars)", InputEngineeringSection.maskPayload("abc"))
        assertEquals("1234…789 (9 chars)", InputEngineeringSection.maskPayload("123456789"))
    }
}
