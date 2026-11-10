package {{ROOT_PACKAGE}}.coretesting

import {{ROOT_PACKAGE}}.libcommon.time.TimeSource

/** A hand-driven monotonic clock. Tests move it with [advanceBy] or set [nowMillis] directly. */
class FakeTimeSource(var nowMillis: Long = 0L) : TimeSource {

    override fun elapsedRealtimeMillis(): Long = nowMillis

    fun advanceBy(millis: Long) {
        require(millis >= 0) { "a monotonic clock never goes back (got $millis)" }
        nowMillis += millis
    }
}
