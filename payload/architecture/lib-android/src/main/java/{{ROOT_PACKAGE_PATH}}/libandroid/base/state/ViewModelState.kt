package {{ROOT_PACKAGE}}.libandroid.base.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Marker for a ViewModel's private, immutable source-of-truth state (`private data class State(...)`). */
interface ViewModelState

/**
 * Replaces the state with a modified copy: `mState.upd { copy(isLoading = false) }`.
 *
 * Built on [update], so concurrent updates never lose a write even if a ViewModel mutates from a
 * background dispatcher.
 */
inline fun <T : ViewModelState> MutableStateFlow<T>.upd(crossinline block: T.() -> T) {
    update { it.block() }
}
