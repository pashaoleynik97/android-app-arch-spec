package {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** A screen's request for the app-level banner: the [data] plus its callbacks. */
@Stable
class AppSnackbarRequest(
    val data: TopSnackbarData,
    val onClose: (() -> Unit)? = null,
    val onRetry: (() -> Unit)? = null,
    val onDismiss: () -> Unit = {},
)

/**
 * The single screen-banner slot the host renders above all of its chrome (so a banner overlaps the app bar
 * instead of being pushed below it). Screens raise banners through [SetAppSnackbar], never through this class.
 *
 * **Clearing is ownership-scoped.** During a navigation both destinations are composed at once — the arriving
 * screen raises its banner while the leaving one is still animating out — so [hide] clears the slot only if it
 * still holds the request being withdrawn; otherwise the leaving screen would take away the arriving screen's
 * banner.
 */
@Stable
class AppSnackbarController {
    var request: AppSnackbarRequest? by mutableStateOf(null)
        private set

    fun show(request: AppSnackbarRequest) {
        this.request = request
    }

    /** Withdraws [request] if it is still the one shown; `null` withdraws nothing. */
    fun hide(request: AppSnackbarRequest?) {
        if (request != null && this.request === request) this.request = null
    }

    /** Clears whatever is shown, whoever raised it. For the host only (e.g. when it re-roots navigation). */
    fun clear() {
        request = null
    }
}

/** No default — the host provides it around the `NavHost`. */
val LocalAppSnackbarController = staticCompositionLocalOf<AppSnackbarController> {
    error("AppSnackbarController not provided. Wrap the NavHost with a CompositionLocalProvider.")
}

/**
 * Raises the app-level banner for the calling screen; `null` [data] raises nothing. The banner is withdrawn
 * automatically when [data] changes or the screen leaves composition — and only ever the screen's own banner.
 *
 * @param onClose the X was tapped (or the body, see [TopSnackbarData.dismissesOnBodyTap]) — clear the notice.
 * @param onDismiss the auto-dismiss time elapsed (only for banners without X) — clear the notice.
 */
@Composable
fun SetAppSnackbar(
    data: TopSnackbarData?,
    onClose: (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
    onRetry: (() -> Unit)? = null,
) {
    val controller = LocalAppSnackbarController.current
    val latestClose by rememberUpdatedState(onClose)
    val latestRetry by rememberUpdatedState(onRetry)
    val latestDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(data) {
        val request = data?.let {
            AppSnackbarRequest(
                data = it,
                onClose = latestClose?.let { { latestClose?.invoke() } },
                onRetry = latestRetry?.let { { latestRetry?.invoke() } },
                onDismiss = { latestDismiss() },
            )
        }
        if (request != null) controller.show(request)
        onDispose { controller.hide(request) }
    }
}
