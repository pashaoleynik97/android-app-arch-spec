package {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.ui.screen

import kotlinx.coroutines.flow.StateFlow

/** The one transient message slot of the screen. The newest notice replaces the previous one. */
enum class {{FEATURE_PASCAL}}Notice { RefreshFailed }

/**
 * What the screen renders. **Plain data, no `Context`**: the composable resolves strings from resources, so
 * previews feed this directly and the ViewModel stays a JVM unit.
 */
sealed interface {{FEATURE_PASCAL}}UxState {
    data object Loading : {{FEATURE_PASCAL}}UxState

    data class Content(
        val itemCount: Int,
        val isRefreshing: Boolean = false,
        val notice: {{FEATURE_PASCAL}}Notice? = null,
    ) : {{FEATURE_PASCAL}}UxState {
        val isEmpty: Boolean get() = itemCount == 0
    }

    /** Nothing to show and the load failed: the body becomes a failure state with a retry. */
    data object Failure : {{FEATURE_PASCAL}}UxState
}

/**
 * The screen's whole surface: state out, intents in, navigation through [uiCallbacks].
 *
 * The ViewModel never touches the `NavController`; it calls [UiCallbacks], which the screen wires to the
 * lambdas `registerGraph` supplies.
 */
interface {{FEATURE_PASCAL}}ViewModelContract {

    interface UiCallbacks {
        fun goBack()
    }

    val uiState: StateFlow<{{FEATURE_PASCAL}}UxState>
    var uiCallbacks: UiCallbacks?

    fun onRetry()
    fun onRefresh()
    fun onNoticeDismissed()
    fun onBackRequested()
}
