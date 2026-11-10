package {{ROOT_PACKAGE}}.auth

import org.junit.Assert.assertEquals
import org.junit.Test
import {{ROOT_PACKAGE}}.coretesting.FakeTimeSource

class UserActivityTrackerTest {

    @Test
    fun `the flow and the direct read always agree`() {
        val clock = FakeTimeSource()
        val tracker = DefaultUserActivityTracker(clock)
        assertEquals(0L, tracker.lastInteractionAtMillis())

        clock.advanceBy(1_234)
        tracker.recordInteraction()

        // The keep-alive gates on the read and wakes on the flow: they must never disagree.
        assertEquals(1_234L, tracker.lastInteractionAtMillis())
        assertEquals(1_234L, tracker.interactions.value)
    }
}
