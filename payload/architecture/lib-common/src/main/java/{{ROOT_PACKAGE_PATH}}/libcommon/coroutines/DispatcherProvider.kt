package {{ROOT_PACKAGE}}.libcommon.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Dispatcher seam. A suspend API that blocks (disk, crypto, parsing) switches to [io] / [default] itself, so
 * it is main-safe for every caller — and tests swap in a test dispatcher so the work stays deterministic.
 *
 * Inject this instead of referencing `Dispatchers.IO` directly in production code.
 */
interface DispatcherProvider {
    /** Blocking IO: disk, network sockets, crypto. */
    val io: CoroutineDispatcher

    /** CPU-bound work. */
    val default: CoroutineDispatcher
}

class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}
