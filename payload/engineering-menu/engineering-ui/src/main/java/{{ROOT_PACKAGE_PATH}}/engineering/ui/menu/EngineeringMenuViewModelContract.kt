package {{ROOT_PACKAGE}}.engineering.ui.menu

import kotlinx.coroutines.flow.StateFlow
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringOption
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringRow

/** One resolved section as the menu renders it. */
data class EngineeringMenuSection(
    val key: String,
    val title: String,
    val rows: List<EngineeringRow>,
)

/** The picker sheet currently open, if any — derived from the live row, so it follows the contributor's state. */
data class EngineeringPickerSheet(
    val sectionKey: String,
    val rowKey: String,
    val title: String,
    val options: List<EngineeringOption>,
    val selectedKey: String?,
)

/** What the menu renders. */
data class EngineeringMenuUxState(
    val query: String = "",
    /** Already filtered by [query]. */
    val sections: List<EngineeringMenuSection> = emptyList(),
    val picker: EngineeringPickerSheet? = null,
) {
    /** A search that matched nothing (distinct from "no sections installed", which cannot happen: Device is built in). */
    val isNoMatch: Boolean get() = sections.isEmpty() && query.isNotBlank()
}

/** The menu's whole surface. */
interface EngineeringMenuViewModelContract {

    val uiState: StateFlow<EngineeringMenuUxState>

    var uiCallbacks: UiCallbacks?

    fun onQueryChange(value: String)

    fun onQueryClear()

    /** A tap on a row (navigate / flip a toggle / open a picker; inert for information and disabled rows). */
    fun onRowClick(sectionKey: String, rowKey: String)

    /** The switch itself was moved (distinct from a row tap so the switch reports the exact target value). */
    fun onToggleChange(sectionKey: String, rowKey: String, checked: Boolean)

    fun onPickerOptionSelected(optionKey: String)

    fun onPickerDismiss()

    fun onBackRequested()

    interface UiCallbacks {
        fun goBack()
        fun navigate(route: String)
    }
}
