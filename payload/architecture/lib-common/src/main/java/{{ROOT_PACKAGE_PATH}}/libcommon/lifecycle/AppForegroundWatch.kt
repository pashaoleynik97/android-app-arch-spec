package {{ROOT_PACKAGE}}.libcommon.lifecycle

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Is the **app** (not one screen) in front of the user right now?
 *
 * The single answer every background job asks before doing periodic work: polling from a phone in a pocket
 * wastes battery and data, and the system throttles it anyway. A flip back to `true` is a job's cue to
 * refresh immediately instead of waiting out its interval.
 *
 * An interface so jobs are tested against a fake.
 */
interface AppForegroundWatch {
    val isForeground: StateFlow<Boolean>
}

/**
 * Backed by [ProcessLifecycleOwner] (`ON_START` / `ON_STOP` of the whole process, debounced by the system
 * across rotations and activity hand-offs — a rotation is not a background trip).
 *
 * The app class field-injects it so it observes from process start; nothing on a screen should be what first
 * constructs it.
 */
@Singleton
class DefaultAppForegroundWatch @Inject constructor() : AppForegroundWatch, DefaultLifecycleObserver {

    private val state = MutableStateFlow(false)
    override val isForeground: StateFlow<Boolean> = state.asStateFlow()

    init {
        // Lifecycle observers must be added on the main thread; Hilt may build this singleton on any thread.
        val register = Runnable { ProcessLifecycleOwner.get().lifecycle.addObserver(this) }
        val main: Looper? = Looper.getMainLooper()
        when {
            main == null -> Unit // plain JVM unit test: there is no process lifecycle to observe
            Looper.myLooper() == main -> register.run()
            else -> Handler(main).post(register)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        state.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        state.value = false
    }
}
