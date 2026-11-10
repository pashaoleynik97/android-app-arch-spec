package {{ROOT_PACKAGE}}.libandroid.host

import androidx.compose.runtime.Composable
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.navigation.NavHostController

/**
 * A capability an **optional** module adds to the app host without the host knowing the module.
 *
 * The host (`:app`'s `AppNavHost`) receives every extension through a Hilt multibinding
 * (`Set<HostExtension>`, declared empty-safe with `@Multibinds`) and nests them around its content. A module
 * contributes one with `@Binds @IntoSet` in its own Hilt module, so installing or removing it never edits Core
 * code. Typical uses: a host-level banner (connectivity), navigation on session end (auth), a global notice.
 *
 * Extensions are composed **outside** the screen banner host and inside the chrome controllers'
 * `CompositionLocalProvider`, so they can read `LocalAppSnackbarController` & co. and draw above screen
 * banners. A lower [order] wraps further out (= higher on screen and in priority).
 */
interface HostExtension {

    /** Nesting order: lower = outer. Leave gaps (10, 20, …) so later modules can slot in between. */
    val order: Int get() = 100

    /** Wraps the host content. Must call [content] exactly once. */
    @Composable
    fun Wrap(navController: NavHostController, content: @Composable () -> Unit)
}

/**
 * An optional module's hook into the single activity's lifecycle (e.g. a hardware driver that must claim a
 * device while the app is in front and release it on pause). The host registers every bound observer on its
 * `MainActivity` lifecycle; contributed with `@Binds @IntoSet` like [HostExtension].
 *
 * Activity lifecycle, not `ProcessLifecycleOwner`: the process owner delays `ON_STOP`, which is too late for
 * anything that holds a shared resource.
 */
interface HostActivityObserver : DefaultLifecycleObserver
