package {{ROOT_PACKAGE}}.engineering.ui.api

/**
 * Recognises a hidden gesture — [requiredTaps] taps, each within [maxGapMillis] of the previous one — for an entry
 * point into the engineering menu that ordinary users will not hit by accident (e.g. 7 taps on the version line of
 * an "About" row). Pure and clock-injected, so it is unit-tested; the screen feeds it taps:
 *
 * ```
 * val detector = remember { TapSequenceDetector() }
 * Text(versionText, Modifier.clickable { if (detector.onTap(SystemClock.uptimeMillis())) openEngineering?.invoke() })
 * ```
 *
 * A pause longer than [maxGapMillis] starts the count again; after a successful detection the count resets, so
 * the next 7 taps trigger again.
 */
class TapSequenceDetector(
    private val requiredTaps: Int = DEFAULT_REQUIRED_TAPS,
    private val maxGapMillis: Long = DEFAULT_MAX_GAP_MILLIS,
) {
    init {
        require(requiredTaps >= 2) { "requiredTaps must be at least 2, was $requiredTaps" }
        require(maxGapMillis > 0) { "maxGapMillis must be positive, was $maxGapMillis" }
    }

    private var count = 0
    private var lastTapAt = Long.MIN_VALUE

    /** Records a tap at [nowMillis] (a monotonic clock) and returns `true` when it completes the sequence. */
    fun onTap(nowMillis: Long): Boolean {
        count = if (count > 0 && nowMillis - lastTapAt <= maxGapMillis) count + 1 else 1
        lastTapAt = nowMillis
        if (count < requiredTaps) return false
        count = 0
        return true
    }

    companion object {
        const val DEFAULT_REQUIRED_TAPS = 7
        const val DEFAULT_MAX_GAP_MILLIS = 600L
    }
}
