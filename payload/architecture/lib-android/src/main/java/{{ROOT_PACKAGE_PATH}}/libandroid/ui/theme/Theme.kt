package {{ROOT_PACKAGE}}.libandroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/**
 * The theme mode chosen at install (`THEME_MODE`): `light-only` (default) or `light-dark`.
 *
 * - `light-only`: one light colour set; the app renders identically whatever the system dark setting is.
 * - `light-dark`: the dark set is used while the system is in dark mode. Token names do not change.
 * Dynamic (wallpaper) colour is not used in either mode.
 */
object {{THEME_PREFIX}}ThemeMode {
    const val VALUE: String = "{{THEME_MODE}}"
    val supportsDark: Boolean get() = VALUE == "light-dark"
}

/**
 * The application theme. Provides the three immutable token holders — [{{THEME_PREFIX}}Colors],
 * [{{THEME_PREFIX}}Typography], [{{THEME_PREFIX}}Dimens] — through CompositionLocals and mirrors colours and
 * typography into a Material 3 [MaterialTheme], so stock components follow the same source of truth.
 *
 * Wrap the activity content once, and every preview:
 * ```
 * {{THEME_PREFIX}}Theme { AppNavHost() }
 * Text(text, color = {{THEME_PREFIX}}Theme.colors.primary, style = {{THEME_PREFIX}}Theme.typography.titleMedium)
 * ```
 */
@Composable
fun {{THEME_PREFIX}}Theme(content: @Composable () -> Unit) {
    // Short-circuits in light-only mode: the system setting is never consulted.
    val dark = {{THEME_PREFIX}}ThemeMode.supportsDark && isSystemInDarkTheme()
    val colors = remember(dark) { if (dark) dark{{THEME_PREFIX}}Colors() else light{{THEME_PREFIX}}Colors() }
    val typography = remember { {{THEME_PREFIX_LOWER}}Typography() }
    val dimens = remember { {{THEME_PREFIX}}Dimens() }

    CompositionLocalProvider(
        Local{{THEME_PREFIX}}Colors provides colors,
        Local{{THEME_PREFIX}}Typography provides typography,
        Local{{THEME_PREFIX}}Dimens provides dimens,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = typography.toMaterialTypography(),
            content = content,
        )
    }
}

/**
 * Token accessor. Shares the theme function's name on purpose (a function and an object may coexist), so
 * call sites read `{{THEME_PREFIX}}Theme.colors.primary`.
 */
object {{THEME_PREFIX}}Theme {
    val colors: {{THEME_PREFIX}}Colors
        @Composable @ReadOnlyComposable
        get() = Local{{THEME_PREFIX}}Colors.current

    val typography: {{THEME_PREFIX}}Typography
        @Composable @ReadOnlyComposable
        get() = Local{{THEME_PREFIX}}Typography.current

    val dimens: {{THEME_PREFIX}}Dimens
        @Composable @ReadOnlyComposable
        get() = Local{{THEME_PREFIX}}Dimens.current
}
