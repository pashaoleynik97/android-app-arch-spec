package {{ROOT_PACKAGE}}.featureapi

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

/**
 * The navigation contract every feature implements.
 *
 * A feature `x` exposes `interface XFeatureApi : FeatureApi` plus its route strings in `:x-ui-api`; its
 * `:x-ui` module provides `XFeatureImpl`, which attaches the feature's destinations to the app's single
 * `NavHost`. The host (`:app`) creates each impl once at startup and calls [addToRegistry]; the `NavHost`
 * then calls [registerGraph] on every registered feature.
 *
 * Why a registry instead of `:app` building every destination itself: the host stays ignorant of screens,
 * and a feature's UI module is the only place that knows its composables and ViewModels.
 */
interface FeatureApi {

    /**
     * Attach this feature's destinations to [navGraphBuilder]. All `NavController` use of the feature lives
     * inside this function: screens receive plain lambdas (`onBack`, `onOpenDetails`), ViewModels never see
     * navigation.
     */
    fun registerGraph(navGraphBuilder: NavGraphBuilder, navController: NavHostController)

    /** Makes this feature discoverable by the host's `NavHost`. Call once, from `Application.onCreate`. */
    fun addToRegistry() {
        FeaturesHolder.addFeature(this)
    }
}
