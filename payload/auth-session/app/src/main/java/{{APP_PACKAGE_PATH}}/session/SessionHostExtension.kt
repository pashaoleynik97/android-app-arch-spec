package {{APP_PACKAGE}}.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.combine
import {{APP_PACKAGE}}.R
import {{ROOT_PACKAGE}}.auth.UserActivityTracker
import {{ROOT_PACKAGE}}.auth.session.SessionNotice
import {{ROOT_PACKAGE}}.auth.session.SessionPolicy
import {{ROOT_PACKAGE}}.auth.session.SessionRoute
import {{ROOT_PACKAGE}}.auth.session.SessionShell
import {{ROOT_PACKAGE}}.libandroid.host.HostExtension
import {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar.LocalAppMenuController
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.LocalAppSnackbarController
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.TopSnackbarData
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.TopSnackbarHost
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.TopSnackbarStyle
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}Icons
import {{ROOT_PACKAGE}}.login.ui.api.LoginRoutes
import javax.inject.Inject

/**
 * The session's part of the host, contributed to `AppNavHost` through the `HostExtension` set (the host never
 * names it). Three jobs:
 *
 * 1. **Navigation follows the session** ([SessionPolicy.route]): no session → the sign-in route as the new root;
 *    a session while on sign-in → the graph's start destination. Re-evaluated on every session change *and*
 *    every back-stack change, so it is idempotent and survives activity recreation and process restore.
 *    Re-rooting also closes the app menu and clears a screen banner the previous screen left (UI-25).
 * 2. **The session notice** ("signed in elsewhere", …) in the outermost banner host — above connectivity and
 *    screen banners, because it explains why the user is suddenly on the sign-in screen.
 * 3. **Activity stamping** for the keep-alive: every pointer-down anywhere in the window is observed (initial
 *    pass, never consumed) and recorded in the [UserActivityTracker]. Hardware keys are not seen here; an app
 *    driven mainly by keys stamps the tracker from its key handling too.
 */
class SessionHostExtension @Inject constructor(
    private val shell: SessionShell,
    private val activityTracker: UserActivityTracker,
) : HostExtension {

    override val order: Int = ORDER

    @Composable
    override fun Wrap(navController: NavHostController, content: @Composable () -> Unit) {
        val notice by shell.notice.collectAsStateWithLifecycle()
        val menu = LocalAppMenuController.current
        val screenBanner = LocalAppSnackbarController.current

        LaunchedEffect(navController) {
            // currentBackStackEntryFlow emits only once the NavHost has set its graph, so navigating is safe here.
            combine(shell.destination, navController.currentBackStackEntryFlow) { destination, entry ->
                SessionPolicy.route(destination, onSignInScreen = entry.destination.route == LoginRoutes.MAIN)
            }.collect { route ->
                when (route) {
                    SessionRoute.ToSignIn -> {
                        menu.close()
                        screenBanner.clear()
                        navController.navigate(LoginRoutes.MAIN) {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    SessionRoute.ToStart -> navController.graph.startDestinationRoute?.let { start ->
                        navController.navigate(start) {
                            popUpTo(LoginRoutes.MAIN) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    SessionRoute.None -> Unit
                }
            }
        }

        TopSnackbarHost(
            data = notice?.toSnackbarData(),
            onClose = shell::dismissNotice,
            onDismiss = shell::dismissNotice,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(activityTracker) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press) activityTracker.recordInteraction()
                            }
                        }
                    },
            ) {
                content()
            }
        }
    }

    private companion object {
        /** Outermost of the host layers: the session explains why everything else just disappeared. */
        const val ORDER = 10
    }
}

@Composable
private fun SessionNotice.toSnackbarData(): TopSnackbarData = TopSnackbarData(
    title = stringResource(
        when (this) {
            SessionNotice.Expired -> R.string.session_notice_expired
            SessionNotice.Inactivity -> R.string.session_notice_inactivity
            SessionNotice.SignedInElsewhere -> R.string.session_notice_signed_in_elsewhere
            SessionNotice.AccessChanged -> R.string.session_notice_access_changed
            SessionNotice.Ended -> R.string.session_notice_ended
        },
    ),
    description = stringResource(R.string.session_notice_sign_in_again),
    style = TopSnackbarStyle.Info,
    icon = {{THEME_PREFIX}}Icons.Info,
    showClose = true,
)
