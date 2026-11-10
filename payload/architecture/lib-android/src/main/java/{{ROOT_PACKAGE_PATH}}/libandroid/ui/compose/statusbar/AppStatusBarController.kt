package {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LocalLifecycleOwner

/** A trailing icon action in the app bar. */
@Immutable
data class StatusBarAction(
    val icon: ImageVector,
    val contentDescription: String?,
    val onClick: () -> Unit,
)

/**
 * What the current screen wants the host's app bar to show. `null` (see [SetAppStatusBar]) hides the bar —
 * for full-bleed screens such as a splash.
 *
 * Build it inside `remember(...)` keyed on its inputs, so the bar is not re-applied on every recomposition.
 */
@Immutable
data class AppStatusBarConfig(
    val title: String,
    val subtitle: String? = null,
    val onBack: (() -> Unit)? = null,
    val actions: List<StatusBarAction> = emptyList(),
)

/**
 * Holds the [AppStatusBarConfig] the host renders above the `NavHost`. Each destination declares its own bar
 * with [SetAppStatusBar] (titles and actions stay screen-owned); the host draws exactly one bar.
 */
@Stable
class AppStatusBarController {
    var config: AppStatusBarConfig? by mutableStateOf(null)
        private set

    fun show(config: AppStatusBarConfig) {
        this.config = config
    }

    fun hide() {
        config = null
    }
}

/** No default — the host provides it around the `NavHost`. */
val LocalAppStatusBarController = staticCompositionLocalOf<AppStatusBarController> {
    error("AppStatusBarController not provided. Wrap the NavHost with a CompositionLocalProvider.")
}

/**
 * Declares the app bar for the calling destination; `null` hides it on this screen.
 *
 * Applied **only while this destination is RESUMED** (now, if it already is, and on every `ON_RESUME`).
 * navigation-compose keeps at most one entry RESUMED, so during an overlapping transition the screen being
 * left can never be the last writer and strand its bar on the next screen. Nothing is hidden on dispose: the
 * outgoing bar stays through the animation and the incoming screen replaces it — no flicker.
 */
@Composable
fun SetAppStatusBar(config: AppStatusBarConfig?) {
    val controller = LocalAppStatusBarController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, config) {
        fun apply() = if (config == null) controller.hide() else controller.show(config)
        val unregister = lifecycleOwner.lifecycle.onResumed(::apply)
        onDispose { unregister() }
    }
}
