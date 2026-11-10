package {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import {{ROOT_PACKAGE}}.coretesting.TestDispatcherProvider
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class Default{{FEATURE_PASCAL}}InteractorTest {

    /** Counts dispatches so the test can tell the interactor really switched to the injected dispatcher. */
    private class CountingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var dispatches = 0
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches++
            delegate.dispatch(context, block)
        }
    }

    @Test
    fun `the scaffolded summary loads as an empty success`() = runTest {
        val interactor = Default{{FEATURE_PASCAL}}Interactor(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))

        assertEquals(
            {{FEATURE_PASCAL}}Result.Success({{FEATURE_PASCAL}}Summary(itemCount = 0)),
            interactor.loadSummary(),
        )
    }

    @Test
    fun `the work runs on the injected io dispatcher, not the caller's`() = runTest {
        val io = CountingDispatcher(StandardTestDispatcher(testScheduler))
        val interactor = Default{{FEATURE_PASCAL}}Interactor(TestDispatcherProvider(io))

        interactor.loadSummary()

        assertTrue("main-safety: the interactor must move its own work off the caller", io.dispatches > 0)
    }
}
