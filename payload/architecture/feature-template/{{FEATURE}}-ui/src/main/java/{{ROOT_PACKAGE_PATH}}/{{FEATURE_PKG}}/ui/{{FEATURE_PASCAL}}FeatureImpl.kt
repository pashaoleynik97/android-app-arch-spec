package {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.ui.api.{{FEATURE_PASCAL}}FeatureApi
import {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.ui.api.{{FEATURE_PASCAL}}Routes
import {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.ui.screen.{{FEATURE_PASCAL}}Screen
import {{ROOT_PACKAGE}}.libandroid.nav.AppTransitions

/**
 * Attaches the {{FEATURE}} destinations to the app's `NavHost`. All navigation of this feature lives here:
 * screens get plain lambdas, ViewModels never see the `NavController`.
 *
 * Routes into a **sibling** feature arrive as constructor parameters supplied by the host (a compile
 * dependency between two `:x-ui` modules would break the module rules). Each defaults to `null` so this feature
 * still builds and runs on its own; a `null` route leaves its action inert. Example:
 * `class {{FEATURE_PASCAL}}FeatureImpl(private val detailsRoute: ((id: String) -> String)? = null)`.
 *
 * Non-root destinations pass the four [AppTransitions]; the entry destination omits them only when it is
 * the app's start destination.
 */
class {{FEATURE_PASCAL}}FeatureImpl : {{FEATURE_PASCAL}}FeatureApi {

    override fun registerGraph(navGraphBuilder: NavGraphBuilder, navController: NavHostController) {
        navGraphBuilder.composable(
            route = {{FEATURE_PASCAL}}Routes.MAIN,
            enterTransition = AppTransitions.enter,
            exitTransition = AppTransitions.exit,
            popEnterTransition = AppTransitions.popEnter,
            popExitTransition = AppTransitions.popExit,
        ) {
            {{FEATURE_PASCAL}}Screen(
                // Only offer "back" when there is somewhere to go: the start destination has nothing below it.
                onBack = if (navController.previousBackStackEntry != null) {
                    { navController.popBackStack() }
                } else {
                    null
                },
            )
        }
    }
}
