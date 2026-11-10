package {{ROOT_PACKAGE}}.connectivity.internal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import {{ROOT_PACKAGE}}.connectivity.NetworkState
import {{ROOT_PACKAGE}}.connectivity.NetworkStateWatch
import {{ROOT_PACKAGE}}.connectivity.di.ConnectivityScope
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [NetworkStateWatch]: one driver coroutine on [ConnectivityScope] combines the [ConnectivityMonitor] with
 * the [ServerHealthProbe].
 *
 * - Two triggers — a link change, or [flush] (which re-reads the current link) — through one `flatMapLatest`, so
 *   a newer trigger **cancels** a probe in flight instead of letting a stale answer land after a fresh one.
 * - No link → OFFLINE at once, without a probe.
 * - The state only ever moves on that short-circuit or a **completed** probe — never reset on a trigger — which is
 *   what gives [flush] its "keep the current value until the fresh result lands" behaviour.
 * - No periodic polling: the OS tells us when the link changes; the user asks for a re-probe.
 */
@Singleton
internal class DefaultNetworkStateWatch @Inject constructor(
    private val monitor: ConnectivityMonitor,
    private val probe: ServerHealthProbe,
    @param:ConnectivityScope private val scope: CoroutineScope,
) : NetworkStateWatch {

    private val logTag = logTag()

    private val mState = MutableStateFlow(NetworkState.UNKNOWN)
    override val networkState: StateFlow<NetworkState> = mState.asStateFlow()

    /** Conflated: a burst of `flush()` calls collapses into one re-probe. */
    private val flushSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        start()
    }

    override fun getCurrentNetworkState(): NetworkState = mState.value

    override fun flush() {
        flushSignal.tryEmit(Unit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun start() {
        val triggers = merge(monitor.linkStatus, flushSignal.map { monitor.current() })
        scope.launch {
            triggers
                .onEach { link -> if (!link.connected) mState.value = NetworkState.OFFLINE }
                .flatMapLatest { link -> flow { emit(resolveFor(link)) } }
                .collect { state ->
                    if (state != mState.value) Reporter.info("network state: $state", where = logTag)
                    mState.value = state
                }
        }
    }

    private suspend fun resolveFor(link: LinkStatus): NetworkState {
        if (!link.connected) return NetworkState.OFFLINE
        val reachable = try {
            probe.isServerReachable()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The probe promises not to throw for IO; anything else is a bug — log it, read it as unreachable.
            Reporter.error("health probe threw", exception = e, where = logTag)
            false
        }
        return NetworkStateResolver.resolve(link, reachable)
    }
}
