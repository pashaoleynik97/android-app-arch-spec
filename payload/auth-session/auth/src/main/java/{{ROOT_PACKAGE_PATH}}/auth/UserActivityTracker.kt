package {{ROOT_PACKAGE}}.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import {{ROOT_PACKAGE}}.libcommon.time.TimeSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When did the user last touch the app? The keep-alive extends a session only if the answer is "since the last
 * refresh" — so an actively used session stays alive and an abandoned one is left to the server's idle window
 * (ADR-0009).
 *
 * The host stamps it on every pointer-down (the session host extension observes the whole window's pointer
 * input without consuming it). Both this and the keep-alive read the same monotonic [TimeSource], so the
 * comparison is meaningful.
 */
interface UserActivityTracker {

    /** Stamp "the user just did something". Cheap; call it freely. */
    fun recordInteraction()

    /** Monotonic time of the last [recordInteraction]; `0` if none. */
    fun lastInteractionAtMillis(): Long

    /** The same value, observable — the keep-alive waits on it while nobody uses the app. */
    val interactions: StateFlow<Long>
}

@Singleton
class DefaultUserActivityTracker @Inject constructor(
    private val clock: TimeSource,
) : UserActivityTracker {

    private val lastInteractionAt = MutableStateFlow(0L)

    override val interactions: StateFlow<Long> = lastInteractionAt.asStateFlow()

    override fun recordInteraction() {
        lastInteractionAt.value = clock.elapsedRealtimeMillis()
    }

    override fun lastInteractionAtMillis(): Long = lastInteractionAt.value
}
