package {{ROOT_PACKAGE}}.connectivity.internal

import {{ROOT_PACKAGE}}.connectivity.NetworkState

/**
 * Pure mapping (link, probe result) → [NetworkState], tested as a full truth table.
 *
 * @param serverReachable the probe's answer, or `null` while a link is up but not probed yet.
 */
internal object NetworkStateResolver {

    fun resolve(link: LinkStatus, serverReachable: Boolean?): NetworkState = when {
        !link.connected -> NetworkState.OFFLINE
        serverReachable == null -> NetworkState.UNKNOWN
        serverReachable -> NetworkState.ONLINE
        else -> NetworkState.SERVER_UNREACHABLE
    }
}
