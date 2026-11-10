package {{ROOT_PACKAGE}}.engineering.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringFeatureApi
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringRoutes
import {{ROOT_PACKAGE}}.engineering.ui.menu.EngineeringMenuScreen
import {{ROOT_PACKAGE}}.libandroid.nav.AppTransitions

/**
 * Registers the engineering menu destination. Registered by the host (`App`, through the installer's
 * `feature-registrations` marker) like any feature; it takes no route from the host, because a row's
 * [{{ROOT_PACKAGE}}.engineering.ui.api.EngineeringControl.Navigate] carries its own route.
 *
 * Back simply pops, so an engineer always walks back out to the screen the hidden entry was on.
 */
class EngineeringFeatureImpl : EngineeringFeatureApi {

    override fun registerGraph(navGraphBuilder: NavGraphBuilder, navController: NavHostController) {
        navGraphBuilder.composable(
            route = EngineeringRoutes.MENU,
            enterTransition = AppTransitions.enter,
            exitTransition = AppTransitions.exit,
            popEnterTransition = AppTransitions.popEnter,
            popExitTransition = AppTransitions.popExit,
        ) {
            EngineeringMenuScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
            )
        }
    }
}
