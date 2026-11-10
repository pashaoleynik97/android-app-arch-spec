package {{ROOT_PACKAGE}}.engineering.ui.menu

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringControl
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringRow
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringSection
import {{ROOT_PACKAGE}}.libandroid.base.BaseViewModel
import {{ROOT_PACKAGE}}.libandroid.base.state.ViewModelState
import {{ROOT_PACKAGE}}.libandroid.base.state.upd
import javax.inject.Inject

/**
 * The engineering menu: every contributed [EngineeringSection], live, searchable, and the controls they carry.
 *
 * - **Sections are a multibinding.** Whatever modules are installed contribute their own rows (device info is
 *   built in; hardware input adds its section when present). The menu knows none of them.
 * - **Rows are collected only while the menu is visible** (`WhileSubscribed`), so live rows (a driver's state, the
 *   last reading) cost nothing while the menu sits in the back stack.
 * - **Controls act on the live row**, looked up by key at tap time, so a tap never fires a stale callback.
 */
@HiltViewModel
class EngineeringMenuViewModel @Inject constructor(
    sections: Set<@JvmSuppressWildcards EngineeringSection>,
) : BaseViewModel(), EngineeringMenuViewModelContract {

    private data class State(
        val query: String = "",
        val openPicker: RowRef? = null,
    ) : ViewModelState

    private data class RowRef(val sectionKey: String, val rowKey: String)

    private val mState = MutableStateFlow(State())

    private val resolved: StateFlow<List<EngineeringMenuSection>> = resolve(sections)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    override val uiState: StateFlow<EngineeringMenuUxState> = combine(mState, resolved) { state, all ->
        EngineeringMenuUxState(
            query = state.query,
            sections = EngineeringMenuFilter.filter(all, state.query),
            picker = state.openPicker?.let { ref -> all.pickerSheet(ref) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), EngineeringMenuUxState())

    override var uiCallbacks: EngineeringMenuViewModelContract.UiCallbacks? = null

    override fun onQueryChange(value: String) {
        // Capped, never character-filtered: a keystroke that silently does nothing is worse than no match.
        mState.upd { copy(query = value.take(MAX_QUERY_LENGTH)) }
    }

    override fun onQueryClear() {
        mState.upd { copy(query = "") }
    }

    override fun onRowClick(sectionKey: String, rowKey: String) {
        val row = findRow(sectionKey, rowKey)?.takeIf { it.enabled } ?: return
        when (val control = row.control) {
            EngineeringControl.None -> Unit
            is EngineeringControl.Navigate -> uiCallbacks?.navigate(control.route)
            is EngineeringControl.Toggle -> control.onCheckedChange(!control.checked)
            is EngineeringControl.Picker -> mState.upd { copy(openPicker = RowRef(sectionKey, rowKey)) }
        }
    }

    override fun onToggleChange(sectionKey: String, rowKey: String, checked: Boolean) {
        val row = findRow(sectionKey, rowKey)?.takeIf { it.enabled } ?: return
        (row.control as? EngineeringControl.Toggle)?.onCheckedChange?.invoke(checked)
    }

    override fun onPickerOptionSelected(optionKey: String) {
        val ref = mState.value.openPicker ?: return
        mState.upd { copy(openPicker = null) }
        val picker = findRow(ref.sectionKey, ref.rowKey)?.takeIf { it.enabled }?.control as? EngineeringControl.Picker
        picker?.onSelect?.invoke(optionKey)
    }

    override fun onPickerDismiss() {
        mState.upd { copy(openPicker = null) }
    }

    override fun onBackRequested() {
        uiCallbacks?.goBack()
    }

    private fun findRow(sectionKey: String, rowKey: String): EngineeringRow? =
        resolved.value.firstOrNull { it.key == sectionKey }?.rows?.firstOrNull { it.key == rowKey }

    private fun List<EngineeringMenuSection>.pickerSheet(ref: RowRef): EngineeringPickerSheet? {
        val row = firstOrNull { it.key == ref.sectionKey }?.rows?.firstOrNull { it.key == ref.rowKey } ?: return null
        val picker = row.control as? EngineeringControl.Picker ?: return null
        return EngineeringPickerSheet(ref.sectionKey, ref.rowKey, row.title, picker.options, picker.selectedKey)
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 100
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Every section's live rows, in section order; one section that never emits holds back none of the others. */
        fun resolve(sections: Set<EngineeringSection>): Flow<List<EngineeringMenuSection>> {
            val ordered = sections.sortedWith(compareBy<EngineeringSection>({ it.order }, { it.key }))
            if (ordered.isEmpty()) return flowOf(emptyList())
            val flows = ordered.map { section ->
                section.rows
                    .map<List<EngineeringRow>, EngineeringMenuSection?> { rows ->
                        EngineeringMenuSection(section.key, section.title, rows)
                    }
                    // Absent until it first emits, instead of holding back the whole menu.
                    .onStart { emit(null) }
            }
            return combine(flows) { it.filterNotNull() }
        }
    }
}
