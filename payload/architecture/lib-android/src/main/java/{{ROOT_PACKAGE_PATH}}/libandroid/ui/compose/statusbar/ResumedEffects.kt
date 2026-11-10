package {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Runs [action] now if this lifecycle is already RESUMED, and again on every `ON_RESUME`. Returns the
 * unregister call for the effect's `onDispose`. The shared mechanism behind the host-chrome `Set…` effects.
 */
internal fun Lifecycle.onResumed(action: () -> Unit): () -> Unit {
    if (currentState.isAtLeast(Lifecycle.State.RESUMED)) action()
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) action()
    }
    addObserver(observer)
    return { removeObserver(observer) }
}
