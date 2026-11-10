package {{ROOT_PACKAGE}}.libandroid.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import kotlinx.coroutines.flow.StateFlow

/**
 * "Navigate for result" over the back stack: a destination hands a value to the one below it through that
 * entry's saved state.
 *
 * Why this transport: it survives process death (unlike an in-memory holder), needs no shared ViewModel and
 * no compile dependency between the two screens — only the key, which the **reading** side owns and
 * publishes in its `:x-ui-api` (or the host hands it to the writer as a seam).
 *
 * Rules:
 * - Values are references (an id, a file path, a flag), never payloads: saved state is size-limited and
 *   must hold only bundle-able types.
 * - The reader consumes **and clears** the value, or it replays on the next return ([ConsumeNavResult] does
 *   both).
 * - Read it on the destination's [NavBackStackEntry] inside `registerGraph`, not from a ViewModel's injected
 *   `SavedStateHandle` — that is a different instance.
 */
class NavResultKey<T : Any>(val name: String) {
    override fun toString(): String = "NavResultKey($name)"
}

fun <T : Any> SavedStateHandle.putResult(key: NavResultKey<T>, value: T) {
    this[key.name] = value
}

fun <T : Any> SavedStateHandle.resultFlow(key: NavResultKey<T>): StateFlow<T?> = getStateFlow(key.name, null)

/**
 * Clears a consumed result. Sets `null` rather than removing the key: removal would detach the
 * [StateFlow] a reader is already collecting, so later results would never reach it.
 */
fun SavedStateHandle.clearResult(key: NavResultKey<*>) {
    this[key.name] = null
}

/** Writes [value] for the destination below and pops back to it. Returns what `popBackStack` returned. */
fun <T : Any> NavController.popBackStackWithResult(key: NavResultKey<T>, value: T): Boolean {
    previousBackStackEntry?.savedStateHandle?.putResult(key, value)
    return popBackStack()
}

/**
 * Delivers a result written for this entry to [onResult] exactly once, then clears it. Call from the
 * destination's `composable(...) { entry -> entry.ConsumeNavResult(KEY) { … } }` block.
 */
@Composable
fun <T : Any> NavBackStackEntry.ConsumeNavResult(key: NavResultKey<T>, onResult: (T) -> Unit) {
    val handle = savedStateHandle
    val value by handle.resultFlow(key).collectAsStateWithLifecycle()
    val latestOnResult by rememberUpdatedState(onResult)
    LaunchedEffect(value) {
        val delivered = value ?: return@LaunchedEffect
        latestOnResult(delivered)
        handle.clearResult(key)
    }
}
