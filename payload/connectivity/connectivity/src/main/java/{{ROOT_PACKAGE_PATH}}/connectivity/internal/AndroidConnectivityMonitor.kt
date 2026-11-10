package {{ROOT_PACKAGE}}.connectivity.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ConnectivityMonitor] over the **default** network callback: the default network already reflects the system's
 * choice between Wi-Fi and cellular, so "the active network has internet capability" is the whole question.
 * Callback-driven — no polling. The only class in the module that needs a device.
 */
@Singleton
internal class AndroidConnectivityMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ConnectivityMonitor {

    private val manager: ConnectivityManager?
        get() = ContextCompat.getSystemService(context, ConnectivityManager::class.java)

    override val linkStatus: Flow<LinkStatus> = run {
        val cm = manager ?: return@run flowOf(LinkStatus.NONE)
        callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(read(cm))
                }

                override fun onLost(network: Network) {
                    trySend(read(cm))
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    trySend(read(cm))
                }
            }
            trySend(read(cm))
            cm.registerDefaultNetworkCallback(callback)
            awaitClose { cm.unregisterNetworkCallback(callback) }
        }.conflate().distinctUntilChanged()
    }

    override fun current(): LinkStatus = manager?.let(::read) ?: LinkStatus.NONE

    private fun read(cm: ConnectivityManager): LinkStatus {
        val caps = cm.activeNetwork?.let(cm::getNetworkCapabilities) ?: return LinkStatus.NONE
        return LinkStatus(connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
    }
}
