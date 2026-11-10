package {{ROOT_PACKAGE}}.input.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import {{ROOT_PACKAGE}}.input.api.InputDriver
import {{ROOT_PACKAGE}}.input.api.InputDriverId
import {{ROOT_PACKAGE}}.input.api.InputDriverState
import {{ROOT_PACKAGE}}.input.api.InputEvent
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag

/**
 * A driver with no hardware behind it: readings are pushed in over adb (see [DebugInputEvents]). It makes the whole
 * chain — controller, resolver, settings, every screen that collects input — exercisable on an emulator.
 *
 * The receiver is registered only while active (resumed) and is **exported**: `adb shell am broadcast` is sent by
 * the shell uid, so a non-exported receiver never hears it. From API 33 a runtime receiver must declare its export
 * state explicitly (`RECEIVER_EXPORTED`, through `ContextCompat` on older levels too). Shipping it exported is
 * acceptable only because this module is `debugImplementation` — it never exists in a release APK.
 */
internal class DebugBroadcastInputDriver(
    private val context: Context,
) : InputDriver {

    private val logTag = logTag()

    override val id: InputDriverId = DebugBroadcastInputProvider.ID

    private val mEvents = MutableSharedFlow<InputEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<InputEvent> = mEvents.asSharedFlow()

    private val mState = MutableStateFlow<InputDriverState>(InputDriverState.Idle)
    override val state: StateFlow<InputDriverState> = mState.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            val event = DebugInputEvents.toEvent(
                data = intent.getStringExtra(DebugInputEvents.EXTRA_DATA),
                kind = intent.getStringExtra(DebugInputEvents.EXTRA_KIND),
                nowMillis = System.currentTimeMillis(),
            )
            if (event == null) {
                Reporter.debug("debug broadcast without a '${DebugInputEvents.EXTRA_DATA}' extra, ignored", where = logTag)
                return
            }
            if (!mEvents.tryEmit(event)) Reporter.warn("debug input event dropped (buffer full)", where = logTag)
        }
    }

    override suspend fun activate() {
        if (registered) return
        mState.value = InputDriverState.Initializing
        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(DebugInputEvents.action(context.packageName)),
                ContextCompat.RECEIVER_EXPORTED,
            )
            registered = true
            mState.value = InputDriverState.Ready
        } catch (e: RuntimeException) {
            mState.value = InputDriverState.Error(e)
        }
    }

    override suspend fun deactivate() = release()

    override fun dispose() = release()

    private fun release() {
        if (!registered) {
            mState.value = InputDriverState.Idle
            return
        }
        registered = false
        runCatching { context.unregisterReceiver(receiver) }
        mState.value = InputDriverState.Idle
    }
}
