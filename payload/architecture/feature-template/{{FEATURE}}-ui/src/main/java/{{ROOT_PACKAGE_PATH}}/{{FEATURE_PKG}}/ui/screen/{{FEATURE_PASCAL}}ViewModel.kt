package {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.ui.screen

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.domain.{{FEATURE_PASCAL}}Interactor
import {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.domain.{{FEATURE_PASCAL}}Result
import {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.domain.{{FEATURE_PASCAL}}Summary
import {{ROOT_PACKAGE}}.libandroid.base.BaseViewModel
import {{ROOT_PACKAGE}}.libandroid.base.state.ViewModelState
import {{ROOT_PACKAGE}}.libandroid.base.state.upd
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import javax.inject.Inject

/**
 * The {{FEATURE}} entry screen.
 *
 * - **One source of truth**: the private [State], changed only through `mState.upd { copy(…) }` and projected
 *   to [{{FEATURE_PASCAL}}UxState] in one place, [State.toUxState].
 * - **A failed refresh keeps the content**: the full-body failure appears only when there is nothing to show;
 *   otherwise the failure becomes a dismissible notice.
 * - **One load in flight**: a new load cancels the previous one.
 * - **Navigation decided before the screen attached is not lost**: it is buffered and delivered when
 *   [uiCallbacks] is set.
 */
@HiltViewModel
class {{FEATURE_PASCAL}}ViewModel @Inject constructor(
    private val interactor: {{FEATURE_PASCAL}}Interactor,
) : BaseViewModel(), {{FEATURE_PASCAL}}ViewModelContract {

    private data class State(
        val summary: {{FEATURE_PASCAL}}Summary? = null,
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val failed: Boolean = false,
        val notice: {{FEATURE_PASCAL}}Notice? = null,
    ) : ViewModelState {
        fun toUxState(): {{FEATURE_PASCAL}}UxState = when {
            summary != null -> {{FEATURE_PASCAL}}UxState.Content(
                itemCount = summary.itemCount,
                isRefreshing = isRefreshing,
                notice = notice,
            )
            failed && !isLoading -> {{FEATURE_PASCAL}}UxState.Failure
            else -> {{FEATURE_PASCAL}}UxState.Loading
        }
    }

    private val mState = MutableStateFlow(State())

    override val uiState: StateFlow<{{FEATURE_PASCAL}}UxState> = mState
        .map { it.toUxState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, mState.value.toUxState())

    private var pendingNavigation: ({{FEATURE_PASCAL}}ViewModelContract.UiCallbacks.() -> Unit)? = null

    override var uiCallbacks: {{FEATURE_PASCAL}}ViewModelContract.UiCallbacks? = null
        set(value) {
            field = value
            if (value != null) {
                pendingNavigation?.let { navigation ->
                    pendingNavigation = null
                    value.navigation()
                }
            }
        }

    private var loadJob: Job? = null

    init {
        load(userInitiated = false)
    }

    // region intents

    override fun onRetry() {
        mState.upd { copy(isLoading = true, failed = false) }
        load(userInitiated = false)
    }

    override fun onRefresh() {
        if (mState.value.isRefreshing) return
        mState.upd { copy(isRefreshing = true) }
        load(userInitiated = true)
    }

    override fun onNoticeDismissed() {
        mState.upd { copy(notice = null) }
    }

    override fun onBackRequested() = navigate { goBack() }

    // endregion

    private fun load(userInitiated: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when (val result = interactor.loadSummary()) {
                is {{FEATURE_PASCAL}}Result.Success -> mState.upd {
                    copy(summary = result.summary, isLoading = false, isRefreshing = false, failed = false)
                }
                {{FEATURE_PASCAL}}Result.Unavailable -> {
                    Reporter.debug("summary unavailable (userInitiated=$userInitiated)", where = logTag)
                    mState.upd {
                        copy(
                            isLoading = false,
                            isRefreshing = false,
                            failed = true,
                            // Only a refresh the user asked for reports its failure; content stays on screen.
                            notice = if (userInitiated && summary != null) {{FEATURE_PASCAL}}Notice.RefreshFailed else notice,
                        )
                    }
                }
            }
        }
    }

    private fun navigate(navigation: {{FEATURE_PASCAL}}ViewModelContract.UiCallbacks.() -> Unit) {
        val callbacks = uiCallbacks
        if (callbacks == null) pendingNavigation = navigation else callbacks.navigation()
    }
}
