package {{ROOT_PACKAGE}}.engineering.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import {{ROOT_PACKAGE}}.coretesting.MainDispatcherRule
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringControl
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringOption
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringRow
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringSection
import {{ROOT_PACKAGE}}.engineering.ui.menu.EngineeringMenuViewModel
import {{ROOT_PACKAGE}}.engineering.ui.menu.EngineeringMenuViewModelContract

class EngineeringMenuViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** A contributor whose rows the test drives, with a live toggle and picker. */
    private class FakeSection(
        override val key: String,
        override val order: Int,
        override val title: String = key,
    ) : EngineeringSection {
        val verbose = MutableStateFlow(false)
        val level = MutableStateFlow("info")
        val toggled = mutableListOf<Boolean>()
        val selected = mutableListOf<String>()
        var disabledRowEnabled = false

        override val rows: Flow<List<EngineeringRow>> = kotlinx.coroutines.flow.combine(verbose, level) { v, l ->
            listOf(
                EngineeringRow("info", "Build", "debug"),
                EngineeringRow("verbose", "Verbose logs", control = EngineeringControl.Toggle(v) { toggled += it }),
                EngineeringRow(
                    key = "level",
                    title = "Log level",
                    description = l,
                    control = EngineeringControl.Picker(
                        options = listOf(EngineeringOption("info", "Info"), EngineeringOption("debug", "Debug")),
                        selectedKey = l,
                    ) { selected += it },
                ),
                EngineeringRow("tools", "Tools", control = EngineeringControl.Navigate("tools/main")),
                EngineeringRow(
                    key = "release-only",
                    title = "Upload logs",
                    control = EngineeringControl.Toggle(false) { toggled += it },
                    enabled = disabledRowEnabled,
                ),
            )
        }
    }

    private class RecordingCallbacks : EngineeringMenuViewModelContract.UiCallbacks {
        val routes = mutableListOf<String>()
        var backs = 0
        override fun goBack() {
            backs++
        }
        override fun navigate(route: String) {
            routes += route
        }
    }

    private val logging = FakeSection("logging", order = 50)
    private val device = object : EngineeringSection {
        override val key = "device"
        override val title = "Device"
        override val order = 0
        override val rows = MutableStateFlow(listOf(EngineeringRow("model", "Device", "Example Phone 7")))
    }

    private fun viewModel(vararg sections: EngineeringSection) = EngineeringMenuViewModel(sections.toSet())

    @Test
    fun `sections are shown in their declared order`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel(logging, device)
        backgroundScope.launch { vm.uiState.collect {} }

        assertEquals(listOf("device", "logging"), vm.uiState.value.sections.map { it.key })
    }

    @Test
    fun `a section that has not emitted yet does not hold back the others`() = runTest(mainDispatcherRule.dispatcher) {
        val silent = object : EngineeringSection {
            override val key = "silent"
            override val title = "Silent"
            override val rows: Flow<List<EngineeringRow>> = emptyFlow()
        }
        val vm = viewModel(silent, device)
        backgroundScope.launch { vm.uiState.collect {} }

        assertEquals(listOf("device"), vm.uiState.value.sections.map { it.key })
    }

    @Test
    fun `the query filters rows and reports a no-match`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel(logging, device)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onQueryChange("phone")
        assertEquals(listOf("model"), vm.uiState.value.sections.flatMap { it.rows }.map { it.key })

        vm.onQueryChange("nothing like this")
        assertTrue(vm.uiState.value.isNoMatch)

        vm.onQueryClear()
        assertEquals(2, vm.uiState.value.sections.size)
    }

    @Test
    fun `tapping a toggle row flips it through the contributor`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel(logging)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onRowClick("logging", "verbose")
        vm.onToggleChange("logging", "verbose", checked = false)

        assertEquals(listOf(true, false), logging.toggled)
    }

    @Test
    fun `a disabled row ignores taps and switch moves`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel(logging)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onRowClick("logging", "release-only")
        vm.onToggleChange("logging", "release-only", checked = true)

        assertTrue(logging.toggled.isEmpty())
    }

    @Test
    fun `a picker opens a sheet from the live row and forwards the choice`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel(logging)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onRowClick("logging", "level")
        val sheet = vm.uiState.value.picker!!
        assertEquals("Log level", sheet.title)
        assertEquals("info", sheet.selectedKey)

        logging.level.value = "debug"
        assertEquals("the sheet follows the contributor's state", "debug", vm.uiState.value.picker?.selectedKey)

        vm.onPickerOptionSelected("info")
        assertEquals(listOf("info"), logging.selected)
        assertNull(vm.uiState.value.picker)
    }

    @Test
    fun `dismissing the picker selects nothing`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel(logging)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onRowClick("logging", "level")
        vm.onPickerDismiss()
        vm.onPickerOptionSelected("debug")

        assertNull(vm.uiState.value.picker)
        assertTrue(logging.selected.isEmpty())
    }

    @Test
    fun `navigate rows and back go through the callbacks, information rows do nothing`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel(logging)
        backgroundScope.launch { vm.uiState.collect {} }
        val callbacks = RecordingCallbacks()
        vm.uiCallbacks = callbacks

        vm.onRowClick("logging", "info")
        vm.onRowClick("logging", "tools")
        vm.onRowClick("logging", "no-such-row")
        vm.onBackRequested()

        assertEquals(listOf("tools/main"), callbacks.routes)
        assertEquals(1, callbacks.backs)
    }

    @Test
    fun `the query is capped`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel(device)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onQueryChange("x".repeat(500))

        assertEquals(100, vm.uiState.value.query.length)
    }
}
