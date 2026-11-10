package {{ROOT_PACKAGE}}.input.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import {{ROOT_PACKAGE}}.input.api.InputEvent

/**
 * Delivers hardware-input events to [onEvent] **only while this destination is RESUMED**.
 *
 * With Navigation Compose each destination has its own lifecycle and only the top entry is RESUMED, so this is what
 * keeps a screen sitting in the back stack from reacting to a reading meant for the screen in front of it. Every
 * screen that consumes `InputController.events` collects through here (via its ViewModel):
 *
 * ```
 * CollectInputWhileResumed(viewModel.inputEvents, viewModel::onInput)
 * ```
 *
 * Handling must be idempotent (a double read is normal hardware behaviour) and an unmatched reading needs visible
 * feedback — silence reads as "the device is broken".
 *
 * It lives in `:input` (the module's only composable) rather than in the UI toolkit because the toolkit must not
 * depend on an optional infra module.
 */
@Composable
fun CollectInputWhileResumed(
    events: Flow<InputEvent>,
    onEvent: (InputEvent) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(events, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            events.collect { latestOnEvent(it) }
        }
    }
}
