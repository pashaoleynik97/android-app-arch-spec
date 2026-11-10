package {{ROOT_PACKAGE}}.connectivity.banner

import {{ROOT_PACKAGE}}.connectivity.NetworkState

/** The two host-level connectivity banners. The host maps each to its text and style. */
enum class ConnectivityBanner { Offline, Restored }

/** What to do with the banner on a state change. */
sealed interface BannerAction {
    /** Leave it as it is — including a banner the user closed while still offline. */
    data object Keep : BannerAction

    data object Clear : BannerAction

    data class Show(val banner: ConnectivityBanner) : BannerAction
}

/**
 * Pure banner rules, tested as a table. A "problem" is a state where the backend cannot be reached
 * ([NetworkState.OFFLINE], [NetworkState.SERVER_UNREACHABLE]); [NetworkState.UNKNOWN] is neutral.
 *
 * - **Offline is shown on entering a problem**, not on every problem emission — so a user who closed it is not
 *   nagged again while still offline (moving between OFFLINE and SERVER_UNREACHABLE keeps it closed).
 * - **Restored is shown only when leaving a problem for ONLINE**; leaving it for UNKNOWN just clears.
 */
object ConnectivityBannerPolicy {

    fun isProblem(state: NetworkState): Boolean =
        state == NetworkState.OFFLINE || state == NetworkState.SERVER_UNREACHABLE

    fun onStateChange(wasProblem: Boolean, newState: NetworkState): BannerAction {
        val problem = isProblem(newState)
        return when {
            problem && !wasProblem -> BannerAction.Show(ConnectivityBanner.Offline)
            !problem && wasProblem && newState == NetworkState.ONLINE -> BannerAction.Show(ConnectivityBanner.Restored)
            !problem && wasProblem -> BannerAction.Clear
            else -> BannerAction.Keep
        }
    }
}
