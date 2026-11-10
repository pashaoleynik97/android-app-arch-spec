package {{ROOT_PACKAGE}}.network.access

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import {{ROOT_PACKAGE}}.network.di.NetworkScope
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [AccessLossBus]. [report] hands the loss to a [Channel] (no coroutine needed on the OkHttp thread); one
 * coroutine on the module's app scope drains it **sequentially**, so two failing requests can never start two
 * competing recoveries.
 *
 * A `Channel` rather than a `SharedFlow`: a shared flow drops what is emitted while nobody collects, and the first
 * report can easily land before the drain has started. The buffer drops the **oldest** on a pathological flood —
 * losing a duplicate report is harmless, blocking the reporting thread is not.
 */
@Singleton
internal class DefaultAccessLossBus @Inject constructor(
    @NetworkScope scope: CoroutineScope,
    filters: Set<@JvmSuppressWildcards AccessLossFilter>,
) : AccessLossBus {

    private val logTag = logTag()

    /** Registration order; offered in reverse. Copy-on-write: registered on the main thread, read by the drain. */
    private val handlers = CopyOnWriteArrayList<AccessLossHandler>()
    private val standing: List<AccessLossHandler> = filters.toList()

    @Volatile
    private var fallback: AccessLossHandler? = null

    private val reports = Channel<AccessLoss>(capacity = BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        scope.launch { for (loss in reports) dispatch(loss) }
    }

    override fun report(loss: AccessLoss) {
        Reporter.warn("access lost: $loss", where = logTag)
        reports.trySend(loss)
    }

    override fun register(handler: AccessLossHandler): AccessLossBus.Registration {
        handlers += handler
        return AccessLossBus.Registration { handlers.remove(handler) }
    }

    override fun setFallback(handler: AccessLossHandler) {
        fallback = handler
    }

    private suspend fun dispatch(loss: AccessLoss) {
        for (handler in handlers.asReversed()) if (offer(handler, loss)) return
        for (handler in standing) if (offer(handler, loss)) return
        val last = fallback
        if (last == null) {
            // Only before the host is up — the user is not inside the app yet.
            Reporter.warn("access loss with no fallback handler: $loss", where = logTag)
        } else {
            offer(last, loss)
        }
    }

    /** A throwing handler must not break the chain; cancellation still propagates (KERNEL-16). */
    private suspend fun offer(handler: AccessLossHandler, loss: AccessLoss): Boolean = try {
        handler.handle(loss)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Reporter.error("access-loss handler failed", exception = e, where = logTag)
        false
    }

    private companion object {
        const val BUFFER = 16
    }
}
