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

/** One entry of the app menu. */
@Immutable
data class AppMenuItem(
    val title: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
)

/**
 * The host-owned app menu (a bottom sheet opened from the app bar's menu button). The **options are
 * screen-owned** — each destination declares what applies to it with [SetAppMenuOptions] — while the sheet
 * and its open/closed state belong to the host.
 *
 * The menu outlives destinations (it is drawn outside the `NavHost`), so when the host re-roots navigation it
 * MUST call [reset]: left open, the menu would keep offering a dead screen's actions over the new root.
 */
@Stable
class AppMenuController {
    var isOpen: Boolean by mutableStateOf(false)
        private set

    var items: List<AppMenuItem> by mutableStateOf(emptyList())
        private set

    val hasItems: Boolean get() = items.isNotEmpty()

    /** Opens the menu; a screen that declared no options has no menu to open. */
    fun open() {
        if (hasItems) isOpen = true
    }

    fun close() {
        isOpen = false
    }

    /** Closes the menu and forgets the options. For the host, whenever it re-roots navigation. */
    fun reset() {
        isOpen = false
        items = emptyList()
    }

    internal fun setItems(items: List<AppMenuItem>) {
        this.items = items
        if (items.isEmpty()) isOpen = false
    }
}

/** No default — the host provides it around the `NavHost`. */
val LocalAppMenuController = staticCompositionLocalOf<AppMenuController> {
    error("AppMenuController not provided. Wrap the NavHost with a CompositionLocalProvider.")
}

/**
 * Declares the app-menu options for the calling destination (empty = no menu button). RESUMED-gated like
 * [SetAppStatusBar]. Build [items] inside `remember(...)`.
 */
@Composable
fun SetAppMenuOptions(items: List<AppMenuItem>) {
    val controller = LocalAppMenuController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, items) {
        val unregister = lifecycleOwner.lifecycle.onResumed { controller.setItems(items) }
        onDispose { unregister() }
    }
}
