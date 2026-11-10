package {{ROOT_PACKAGE}}.connectivity.internal

import kotlinx.coroutines.flow.Flow

/** The default network as the OS reports it: is there a link with internet capability? */
data class LinkStatus(val connected: Boolean) {
    companion object {
        val NONE = LinkStatus(connected = false)
        val UP = LinkStatus(connected = true)
    }
}

/** Seam over `ConnectivityManager`, so the state engine is JVM-tested against a fake. */
interface ConnectivityMonitor {

    /** The current [LinkStatus] on subscription, then every change. */
    val linkStatus: Flow<LinkStatus>

    /** One read of the current status (used by `flush()`). */
    fun current(): LinkStatus
}
