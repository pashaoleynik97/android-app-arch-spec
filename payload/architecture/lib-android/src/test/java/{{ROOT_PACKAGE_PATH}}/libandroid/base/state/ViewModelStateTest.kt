package {{ROOT_PACKAGE}}.libandroid.base.state

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewModelStateTest {

    private data class State(val count: Int = 0, val label: String = "") : ViewModelState

    @Test
    fun `upd replaces the state with the modified copy`() {
        val state = MutableStateFlow(State())

        state.upd { copy(count = count + 1) }
        state.upd { copy(label = "done") }

        assertEquals(State(count = 1, label = "done"), state.value)
    }
}
