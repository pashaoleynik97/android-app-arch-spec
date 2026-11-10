package {{ROOT_PACKAGE}}.libcommon.time

import android.os.SystemClock
import javax.inject.Inject

/**
 * Monotonic clock seam for anything that measures durations: timeouts, retry budgets, "refresh when older
 * than", elapsed timers.
 *
 * Monotonic (time since boot) on purpose: unlike the wall clock it never jumps when the user or the network
 * changes the time, which would make a scheduled action fire far too early or too late. Tests inject a
 * fake that they advance by hand.
 */
interface TimeSource {
    fun elapsedRealtimeMillis(): Long
}

class SystemClockTimeSource @Inject constructor() : TimeSource {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}
