package {{ROOT_PACKAGE}}.libandroid.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The app's one font family. Install answer `FONT_FAMILY` = `{{FONT_FAMILY}}`.
 *
 * `system` renders as [FontFamily.Default]. To bundle a design font:
 * 1. drop its static faces into `lib-android/src/main/res/font/` (lowercase file names, e.g. `brand_regular.ttf`,
 *    `brand_bold.ttf`) together with the font's licence file;
 * 2. replace the value below with
 *    `FontFamily(Font(R.font.brand_regular, FontWeight.Normal), Font(R.font.brand_bold, FontWeight.Bold), …)`
 *    (`import androidx.compose.ui.text.font.Font` and `{{ROOT_PACKAGE}}.libandroid.R`).
 * Every text style below picks it up; nothing else changes.
 */
val {{THEME_PREFIX}}FontFamily: FontFamily = FontFamily.Default

/**
 * Named text styles of the design system. Read as `{{THEME_PREFIX}}Theme.typography.<style>`.
 *
 * The scale starts as the Material 3 baseline so it is sensible before a design exists; when the design's
 * named text styles arrive, map each one to a slot here (add a slot for a style Material lacks) instead of
 * inventing sizes in screens. Styles carry no colour — text inherits the ambient content colour.
 */
@Immutable
data class {{THEME_PREFIX}}Typography(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
)

private fun style(size: Int, lineHeight: Int, weight: FontWeight, letterSpacing: Double = 0.0) = TextStyle(
    fontFamily = {{THEME_PREFIX}}FontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

fun {{THEME_PREFIX_LOWER}}Typography(): {{THEME_PREFIX}}Typography = {{THEME_PREFIX}}Typography(
    displayLarge = style(57, 64, FontWeight.Normal),
    displayMedium = style(45, 52, FontWeight.Normal),
    displaySmall = style(36, 44, FontWeight.Normal),
    headlineLarge = style(32, 40, FontWeight.SemiBold),
    headlineMedium = style(28, 36, FontWeight.SemiBold),
    headlineSmall = style(24, 32, FontWeight.SemiBold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.15),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.5),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.25),
    bodySmall = style(12, 16, FontWeight.Normal, 0.4),
    labelLarge = style(16, 20, FontWeight.SemiBold, 0.1),
    labelMedium = style(12, 16, FontWeight.Medium, 0.5),
    labelSmall = style(11, 16, FontWeight.Medium, 0.5),
)

/** Projects the scale onto Material 3 [Typography] so stock components use the same styles. */
fun {{THEME_PREFIX}}Typography.toMaterialTypography(): Typography = Typography(
    displayLarge = displayLarge,
    displayMedium = displayMedium,
    displaySmall = displaySmall,
    headlineLarge = headlineLarge,
    headlineMedium = headlineMedium,
    headlineSmall = headlineSmall,
    titleLarge = titleLarge,
    titleMedium = titleMedium,
    titleSmall = titleSmall,
    bodyLarge = bodyLarge,
    bodyMedium = bodyMedium,
    bodySmall = bodySmall,
    labelLarge = labelLarge,
    labelMedium = labelMedium,
    labelSmall = labelSmall,
)

/** No default — a missing provider is a programming error and fails loudly. */
val Local{{THEME_PREFIX}}Typography = staticCompositionLocalOf<{{THEME_PREFIX}}Typography> {
    error("No {{THEME_PREFIX}}Typography provided. Wrap content in {{THEME_PREFIX}}Theme { }.")
}
