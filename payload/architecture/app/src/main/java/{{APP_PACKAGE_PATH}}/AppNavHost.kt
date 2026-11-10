package {{APP_PACKAGE}}

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import {{ROOT_PACKAGE}}.featureapi.FeaturesHolder
import {{ROOT_PACKAGE}}.libandroid.host.HostExtension
import {{ROOT_PACKAGE}}.home.ui.api.HomeRoutes
import {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar.AppMenuController
import {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar.AppMenuSheetContent
import {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar.AppStatusBar
import {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar.AppStatusBarController
import {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar.LocalAppMenuController
import {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar.LocalAppStatusBarController
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.AppSnackbarController
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.LocalAppSnackbarController
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.TopSnackbarHost
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}BottomSheet
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme

/**
 * The app's single `NavHost` and the host chrome around it.
 *
 * - **Destinations come from the registry**: every feature registered in [App] attaches its own graph; the
 *   host knows no screens. The start destination is the home feature's main route.
 * - **Chrome lives outside the `NavHost`** and is driven by screens through CompositionLocal controllers:
 *   the app bar (`SetAppStatusBar`), the screen banner (`SetAppSnackbar`) and the app menu
 *   (`SetAppMenuOptions`). Banners are layered by nesting `TopSnackbarHost`s — outermost = highest
 *   priority — so a host-level notice (session, connectivity) is added as an outer host around this one.
 * - **Optional modules extend the host without the host knowing them**: [hostExtensions] (a Hilt
 *   multibinding, empty on a Core-only install) are nested around the screen banner host, lowest `order`
 *   outermost. See [HostExtension].
 * - **Insets are handled once, here**: the activity is edge-to-edge; the `NavHost` is padded from the
 *   navigation bar and consumes the status-bar inset while the app bar (which pads itself) is shown.
 */
@Composable
fun AppNavHost(hostExtensions: Set<HostExtension>, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val statusBarController = remember { AppStatusBarController() }
    val snackbarController = remember { AppSnackbarController() }
    val menuController = remember { AppMenuController() }

    // Menu options are screen-owned, but the menu outlives destinations: forget the previous screen's options
    // on every destination change. The arriving screen re-declares its own when it resumes.
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, _, _ -> menuController.reset() }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    CompositionLocalProvider(
        LocalAppStatusBarController provides statusBarController,
        LocalAppSnackbarController provides snackbarController,
        LocalAppMenuController provides menuController,
    ) {
        val extensions = remember(hostExtensions) { hostExtensions.sortedBy { it.order } }
        WrapWithExtensions(extensions, navController) {
            // Screen-raised banner — anchored to the top of the whole app, so it overlaps the app bar.
            val snackbarRequest = snackbarController.request
            TopSnackbarHost(
                data = snackbarRequest?.data,
                onRetry = snackbarRequest?.onRetry,
                onClose = snackbarRequest?.onClose,
                onDismiss = snackbarRequest?.onDismiss ?: {},
                modifier = modifier.background({{THEME_PREFIX}}Theme.colors.background),
            ) {
                val barConfig = statusBarController.config
                Column(modifier = Modifier.fillMaxSize()) {
                    if (barConfig != null) {
                        AppStatusBar(
                            config = barConfig,
                            onMenuClick = if (menuController.hasItems) menuController::open else null,
                            menuContentDescription = stringResource(R.string.app_menu),
                        )
                    }
                    val contentModifier = Modifier
                        .weight(1f)
                        .navigationBarsPadding()
                        .let { if (barConfig != null) it.consumeWindowInsets(WindowInsets.statusBars) else it }
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoutes.MAIN,
                        modifier = contentModifier,
                    ) {
                        FeaturesHolder.features().forEach { feature ->
                            feature.registerGraph(navGraphBuilder = this, navController = navController)
                        }
                    }
                }

                if (menuController.isOpen) {
                    {{THEME_PREFIX}}BottomSheet(onDismissRequest = menuController::close) {
                        AppMenuSheetContent(items = menuController.items, onClose = menuController::close)
                    }
                }
            }
        }
    }
}

/** Nests [extensions] (sorted, outermost first) around [content]. */
@Composable
private fun WrapWithExtensions(
    extensions: List<HostExtension>,
    navController: androidx.navigation.NavHostController,
    content: @Composable () -> Unit,
) {
    if (extensions.isEmpty()) {
        content()
        return
    }
    extensions.first().Wrap(navController) {
        WrapWithExtensions(extensions.drop(1), navController, content)
    }
}
