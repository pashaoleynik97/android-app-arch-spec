package {{ROOT_PACKAGE}}.connectivity

import kotlinx.coroutines.flow.StateFlow

/** The app's single source of truth for connectivity: inject it anywhere; one singleton, one probe at a time. */
interface NetworkStateWatch {

    /** Hot; always has a value. */
    val networkState: StateFlow<NetworkState>

    /** The current value, for one-off checks ("skip the remote call when clearly offline"). */
    fun getCurrentNetworkState(): NetworkState

    /**
     * Re-probe now (a "Retry" button). Does **not** reset the current value: [networkState] keeps it until the
     * fresh result lands, so the UI does not flicker through UNKNOWN. A burst of calls costs one probe.
     */
    fun flush()
}
