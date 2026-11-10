package {{APP_PACKAGE}}.connectivity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import {{APP_PACKAGE}}.R
import {{ROOT_PACKAGE}}.connectivity.banner.ConnectivityBanner
import {{ROOT_PACKAGE}}.connectivity.banner.ConnectivityBannerController
import {{ROOT_PACKAGE}}.libandroid.host.HostExtension
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.TopSnackbarData
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.TopSnackbarHost
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.TopSnackbarStyle
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}Icons
import javax.inject.Inject

/**
 * The connectivity banner layer of the host: below session and access notices, above screen banners (order 30).
 * Offline carries retry + close and stays until closed or recovered; Restored has no close and times out
 * (`TopSnackbarData.autoDismissMillis`). Retry is disabled while a re-probe spins.
 */
class ConnectivityBannerExtension @Inject constructor(
    private val controller: ConnectivityBannerController,
) : HostExtension {

    override val order: Int = 30

    @Composable
    override fun Wrap(navController: NavHostController, content: @Composable () -> Unit) {
        val banner by controller.banner.collectAsStateWithLifecycle()
        val retrying by controller.retrying.collectAsStateWithLifecycle()
        TopSnackbarHost(
            data = banner?.toSnackbarData(),
            onRetry = if (retrying) null else controller::onRetry,
            onClose = controller::onDismiss,
            onDismiss = controller::onDismiss,
            content = content,
        )
    }
}

@Composable
private fun ConnectivityBanner.toSnackbarData(): TopSnackbarData = when (this) {
    ConnectivityBanner.Offline -> TopSnackbarData(
        title = stringResource(R.string.connectivity_offline_title),
        description = stringResource(R.string.connectivity_offline_message),
        style = TopSnackbarStyle.Warning,
        icon = {{THEME_PREFIX}}Icons.AlertTriangle,
        showRetry = true,
        showClose = true,
    )
    ConnectivityBanner.Restored -> TopSnackbarData(
        title = stringResource(R.string.connectivity_restored_title),
        style = TopSnackbarStyle.Success,
        icon = {{THEME_PREFIX}}Icons.CheckCircle,
    )
}
