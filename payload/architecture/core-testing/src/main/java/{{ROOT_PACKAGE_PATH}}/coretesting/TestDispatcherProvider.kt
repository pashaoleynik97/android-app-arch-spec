package {{ROOT_PACKAGE}}.coretesting

import kotlinx.coroutines.CoroutineDispatcher
import {{ROOT_PACKAGE}}.libcommon.coroutines.DispatcherProvider

/**
 * A [DispatcherProvider] that routes both [io] and [default] to one test dispatcher, so work a class moves
 * off the main thread still runs on the test scheduler (virtual time, deterministic ordering).
 */
class TestDispatcherProvider(dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}
