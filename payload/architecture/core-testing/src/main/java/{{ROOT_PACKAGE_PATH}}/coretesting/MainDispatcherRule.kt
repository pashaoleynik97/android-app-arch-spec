package {{ROOT_PACKAGE}}.coretesting

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Replaces `Dispatchers.Main` for the duration of a test, so code using `viewModelScope` runs on a test
 * dispatcher (there is no Android main looper on the JVM).
 *
 * ```
 * @get:Rule val mainDispatcherRule = MainDispatcherRule()
 * @Test fun `x`() = runTest(mainDispatcherRule.dispatcher) { … }
 * ```
 *
 * The default [UnconfinedTestDispatcher] runs launched coroutines eagerly, which suits ViewModel tests that
 * assert `uiState.value` right after an intent. Pass a `StandardTestDispatcher` to control ordering by hand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}
