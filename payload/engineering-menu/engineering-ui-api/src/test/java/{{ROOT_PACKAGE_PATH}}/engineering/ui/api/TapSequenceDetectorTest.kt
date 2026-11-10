package {{ROOT_PACKAGE}}.engineering.ui.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapSequenceDetectorTest {

    private fun TapSequenceDetector.tap(times: Int, start: Long = 0, gap: Long = 100): List<Boolean> =
        (0 until times).map { onTap(start + it * gap) }

    @Test
    fun `the seventh quick tap triggers, not the sixth`() {
        val results = TapSequenceDetector().tap(7)

        assertEquals(List(6) { false } + true, results)
    }

    @Test
    fun `a pause longer than the gap restarts the count`() {
        val detector = TapSequenceDetector(requiredTaps = 3, maxGapMillis = 500)

        assertFalse(detector.onTap(0))
        assertFalse(detector.onTap(400))
        assertFalse("too slow: this is tap 1 again", detector.onTap(1_000))
        assertFalse(detector.onTap(1_200))
        assertTrue(detector.onTap(1_400))
    }

    @Test
    fun `a gap exactly at the limit still counts`() {
        val detector = TapSequenceDetector(requiredTaps = 2, maxGapMillis = 500)

        assertFalse(detector.onTap(0))
        assertTrue(detector.onTap(500))
    }

    @Test
    fun `after triggering the next full sequence triggers again`() {
        val detector = TapSequenceDetector(requiredTaps = 3)

        assertTrue(detector.tap(3).last())
        assertFalse("the count reset after the trigger", detector.onTap(250))
        assertTrue(detector.tap(2, start = 300).last())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a single-tap sequence is rejected - it would not be hidden`() {
        TapSequenceDetector(requiredTaps = 1)
    }
}
