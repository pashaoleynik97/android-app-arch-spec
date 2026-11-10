package {{APP_PACKAGE}}.access

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import {{APP_PACKAGE}}.R
import {{ROOT_PACKAGE}}.auth.access.AccessLossFallback
import {{ROOT_PACKAGE}}.auth.access.AccessNotice
import {{ROOT_PACKAGE}}.libandroid.host.HostExtension
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.TopSnackbarData
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.TopSnackbarHost
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.TopSnackbarStyle
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}Icons
import javax.inject.Inject

/**
 * Renders the access-loss fallback's notice in its own banner layer: under the session layer (a dead session
 * explains more than a refused action) and above connectivity and screen banners. Injecting [fallback] here is
 * also what constructs it — and so installs it as the bus fallback — when the activity starts.
 */
class AccessLossHostExtension @Inject constructor(
    private val fallback: AccessLossFallback,
) : HostExtension {

    override val order: Int = 20

    @Composable
    override fun Wrap(navController: NavHostController, content: @Composable () -> Unit) {
        val notice by fallback.notice.collectAsStateWithLifecycle()
        TopSnackbarHost(
            data = notice?.toSnackbarData(),
            onClose = fallback::dismissNotice,
            onDismiss = fallback::dismissNotice,
            content = content,
        )
    }
}

@Composable
private fun AccessNotice.toSnackbarData(): TopSnackbarData = when (this) {
    AccessNotice.Forbidden -> TopSnackbarData(
        title = stringResource(R.string.access_notice_forbidden),
        style = TopSnackbarStyle.Warning,
        icon = {{THEME_PREFIX}}Icons.AlertTriangle,
        showClose = true,
    )
}
