package {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.ui.screen

import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import {{ROOT_PACKAGE}}.coretesting.MainDispatcherRule
import {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.domain.{{FEATURE_PASCAL}}Result
import {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.domain.{{FEATURE_PASCAL}}Summary

class {{FEATURE_PASCAL}}ViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val interactor = Fake{{FEATURE_PASCAL}}Interactor()

    private class RecordingCallbacks : {{FEATURE_PASCAL}}ViewModelContract.UiCallbacks {
        var backs = 0
        override fun goBack() {
            backs++
        }
    }

    private fun vm() = {{FEATURE_PASCAL}}ViewModel(interactor)

    // region loading

    @Test
    fun `shows loading until the first load answers`() {
        interactor.gate = CompletableDeferred()
        val vm = vm()

        assertEquals({{FEATURE_PASCAL}}UxState.Loading, vm.uiState.value)

        interactor.gate!!.complete({{FEATURE_PASCAL}}Result.Success({{FEATURE_PASCAL}}Summary(itemCount = 2)))
        assertEquals({{FEATURE_PASCAL}}UxState.Content(itemCount = 2), vm.uiState.value)
    }

    @Test
    fun `a first load that fails shows the failure body, and retry recovers`() {
        interactor.result = {{FEATURE_PASCAL}}Result.Unavailable
        val vm = vm()
        assertEquals({{FEATURE_PASCAL}}UxState.Failure, vm.uiState.value)

        interactor.result = {{FEATURE_PASCAL}}Result.Success({{FEATURE_PASCAL}}Summary(itemCount = 1))
        vm.onRetry()

        assertEquals({{FEATURE_PASCAL}}UxState.Content(itemCount = 1), vm.uiState.value)
        assertEquals(2, interactor.calls)
    }

    // endregion

    // region refresh

    @Test
    fun `a failed refresh keeps the content and raises a dismissible notice`() {
        val vm = vm()
        interactor.result = {{FEATURE_PASCAL}}Result.Unavailable

        vm.onRefresh()

        assertEquals(
            {{FEATURE_PASCAL}}UxState.Content(itemCount = 3, notice = {{FEATURE_PASCAL}}Notice.RefreshFailed),
            vm.uiState.value,
        )

        vm.onNoticeDismissed()
        assertEquals({{FEATURE_PASCAL}}UxState.Content(itemCount = 3), vm.uiState.value)
    }

    @Test
    fun `a refresh in flight shows the spinner and ignores a second refresh`() {
        val vm = vm()
        interactor.gate = CompletableDeferred()

        vm.onRefresh()
        vm.onRefresh()

        assertEquals({{FEATURE_PASCAL}}UxState.Content(itemCount = 3, isRefreshing = true), vm.uiState.value)
        assertEquals(2, interactor.calls)
    }

    // endregion

    // region navigation

    @Test
    fun `back goes through the callbacks`() {
        val vm = vm()
        val callbacks = RecordingCallbacks().also { vm.uiCallbacks = it }

        vm.onBackRequested()

        assertEquals(1, callbacks.backs)
    }

    @Test
    fun `a navigation decided before the callbacks attach is delivered when they do`() {
        val vm = vm()
        vm.onBackRequested()

        val callbacks = RecordingCallbacks().also { vm.uiCallbacks = it }

        assertEquals(1, callbacks.backs)
    }

    // endregion
}
