package {{ROOT_PACKAGE}}.coretesting

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter

@OptIn(ExperimentalCoroutinesApi::class)
class CoreTestingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val reporter = RecordingReporter()

    @Test
    fun `the fake clock only moves when told to`() {
        val clock = FakeTimeSource(nowMillis = 100)
        clock.advanceBy(50)
        assertEquals(150, clock.elapsedRealtimeMillis())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `the fake clock refuses to go back`() {
        FakeTimeSource().advanceBy(-1)
    }

    @Test
    fun `the recording reporter captures lines while installed`() {
        Reporter.debug("hello", where = "CoreTestingTest")
        assertTrue(reporter.lines.contains("[D][CoreTestingTest] hello"))
    }

    @Test
    fun `main is replaced so work launched on it runs`() = runTest(mainDispatcherRule.dispatcher) {
        var ran = false
        launch(Dispatchers.Main) { ran = true }
        assertTrue(ran)
    }

    @Test
    fun `io work runs on the test scheduler`() = runTest {
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val result = withContext(dispatchers.io) { 42 }
        assertEquals(42, result)
    }
}
