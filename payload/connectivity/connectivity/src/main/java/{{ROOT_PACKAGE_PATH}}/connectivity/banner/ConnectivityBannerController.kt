package {{ROOT_PACKAGE}}.connectivity.banner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import {{ROOT_PACKAGE}}.connectivity.NetworkStateWatch
import {{ROOT_PACKAGE}}.connectivity.di.ConnectivityScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The host's connectivity banner as state, outside any activity (so it survives recreation) and free of UI (so it
 * is JVM-tested). The connectivity host extension in `:app` renders [banner] and forwards the user's actions.
 *
 * Injecting this is also what first constructs — and so starts — the [NetworkStateWatch].
 */
@Singleton
class ConnectivityBannerController @Inject constructor(
    private val watch: NetworkStateWatch,
    @param:ConnectivityScope private val scope: CoroutineScope,
) {

    private val mBanner = MutableStateFlow<ConnectivityBanner?>(null)
    val banner: StateFlow<ConnectivityBanner?> = mBanner.asStateFlow()

    /** True while a user-requested re-probe runs (at least [MIN_RETRY_SPIN_MILLIS], so the tap visibly did something). */
    private val mRetrying = MutableStateFlow(false)
    val retrying: StateFlow<Boolean> = mRetrying.asStateFlow()

    private var spinJob: Job? = null

    init {
        scope.launch {
            // Baseline healthy: the first resolved problem raises Offline.
            var wasProblem = false
            watch.networkState.collect { state ->
                when (val action = ConnectivityBannerPolicy.onStateChange(wasProblem, state)) {
                    is BannerAction.Show -> mBanner.value = action.banner
                    BannerAction.Clear -> mBanner.value = null
                    BannerAction.Keep -> Unit
                }
                wasProblem = ConnectivityBannerPolicy.isProblem(state)
            }
        }
    }

    /** The banner's retry: re-probe, spin meanwhile. Ignored while a retry is already spinning. */
    fun onRetry() {
        if (mRetrying.value) return
        mRetrying.value = true
        watch.flush()
        spinJob?.cancel()
        spinJob = scope.launch {
            delay(MIN_RETRY_SPIN_MILLIS)
            mRetrying.value = false
        }
    }

    /** Closed by the user, or the auto-dismiss of the restored banner elapsed. */
    fun onDismiss() {
        mBanner.value = null
    }

    companion object {
        const val MIN_RETRY_SPIN_MILLIS = 1_200L
    }
}
