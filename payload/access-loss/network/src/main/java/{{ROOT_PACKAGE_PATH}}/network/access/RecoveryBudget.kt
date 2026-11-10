package {{ROOT_PACKAGE}}.network.access

import {{ROOT_PACKAGE}}.libcommon.time.TimeSource
import kotlin.time.Duration

/**
 * A sliding-window budget for **automatic** recoveries: at most [maxAttempts] inside any [window]. Attempts spread
 * over a day never add up to a loop; a burst does. On the monotonic clock, so a wall-clock change cannot refill it.
 *
 * Reusable for any self-healing action that could spin (re-establishing a scope, re-running a handshake).
 */
class RecoveryBudget(
    private val maxAttempts: Int,
    private val window: Duration,
    private val clock: TimeSource,
) {
    private val attempts = ArrayDeque<Long>()

    /** Records an attempt and answers whether it is within budget. */
    @Synchronized
    fun tryAcquire(): Boolean {
        val now = clock.elapsedRealtimeMillis()
        val cutoff = now - window.inWholeMilliseconds
        while (attempts.isNotEmpty() && attempts.first() <= cutoff) attempts.removeFirst()
        attempts.addLast(now)
        return attempts.size <= maxAttempts
    }

    /** A session boundary hands the next session a full budget. */
    @Synchronized
    fun reset() = attempts.clear()
}
