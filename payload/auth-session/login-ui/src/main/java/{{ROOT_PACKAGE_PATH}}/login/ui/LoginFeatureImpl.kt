package {{ROOT_PACKAGE}}.login.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import {{ROOT_PACKAGE}}.login.ui.api.LoginFeatureApi
import {{ROOT_PACKAGE}}.login.ui.api.LoginRoutes
import {{ROOT_PACKAGE}}.login.ui.screen.LoginScreen

/**
 * Attaches the sign-in destination. It has no navigation of its own: the session host extension in `:app` moves
 * the user here when there is no session and away from here when one starts, so a sign-in that succeeds needs
 * no callback — and a session that dies anywhere lands here the same way. No transitions: it is always entered
 * as a new root.
 */
class LoginFeatureImpl : LoginFeatureApi {

    override fun registerGraph(navGraphBuilder: NavGraphBuilder, navController: NavHostController) {
        navGraphBuilder.composable(route = LoginRoutes.MAIN) {
            LoginScreen()
        }
    }
}
