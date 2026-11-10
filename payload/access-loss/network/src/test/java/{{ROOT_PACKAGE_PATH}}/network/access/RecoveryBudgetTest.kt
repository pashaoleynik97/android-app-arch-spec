package {{ROOT_PACKAGE}}.network.access

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import {{ROOT_PACKAGE}}.coretesting.FakeTimeSource
import kotlin.time.Duration.Companion.seconds

class RecoveryBudgetTest {

    private val clock = FakeTimeSource()
    private val budget = RecoveryBudget(maxAttempts = 3, window = 60.seconds, clock = clock)

    @Test
    fun `a burst beyond the budget is refused`() {
        repeat(3) { assertTrue(budget.tryAcquire()) }

        assertFalse(budget.tryAcquire())
    }

    @Test
    fun `attempts older than the window no longer count`() {
        repeat(3) { budget.tryAcquire() }

        clock.advanceBy(60_000)

        assertTrue("the window slid past the earlier burst", budget.tryAcquire())
    }

    @Test
    fun `attempts spread out never add up to a loop`() {
        repeat(20) {
            assertTrue(budget.tryAcquire())
            clock.advanceBy(25_000)
        }
    }

    @Test
    fun `a reset refills the budget`() {
        repeat(4) { budget.tryAcquire() }

        budget.reset()

        assertTrue(budget.tryAcquire())
    }
}
