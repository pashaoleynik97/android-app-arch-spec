package {{ROOT_PACKAGE}}.libandroid.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Raw physical palette. Never referenced from UI — screens read the semantic roles on [{{THEME_PREFIX}}Colors].
 *
 * The four brand/status colours come from the install answers (`COLOR_PRIMARY`, `COLOR_ERROR`,
 * `COLOR_SUCCESS`, `COLOR_WARNING`); everything else is a neutral scale. When the real design arrives, edit
 * the values here (or re-render the palette from new answers) — call sites do not change.
 */
private object Palette {
    val Brand = hexColor("{{COLOR_PRIMARY}}")
    val Error = hexColor("{{COLOR_ERROR}}")
    val Success = hexColor("{{COLOR_SUCCESS}}")
    val Warning = hexColor("{{COLOR_WARNING}}")

    val White = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1C1C1E)
    val InkSecondary = Color(0xFF3C3C43)
    val Grey = Color(0xFF8E8E93)
    val GreyLight = Color(0xFFE5E5EA)
    val GreyLighter = Color(0xFFF2F2F7)
    val Hairline = Color(0xFFD8D8DC)
    val HairlineStrong = Color(0xFFA0A0A6)

    val Night = Color(0xFF121214)
    val NightSurface = Color(0xFF1E1E21)
    val NightSurfaceVariant = Color(0xFF2C2C30)
    val NightInk = Color(0xFFEDEDF0)
    val NightInkSecondary = Color(0xFFC7C7CC)
    val NightHairline = Color(0xFF3A3A3F)
    val NightHairlineStrong = Color(0xFF636366)
}

/**
 * Parses a `#RRGGBB` string into an opaque [Color]. The palette is rendered from install answers as strings
 * so that any valid answer produces compiling code; a malformed value fails loudly at first use.
 */
internal fun hexColor(hex: String): Color {
    require(hex.length == 7 && hex[0] == '#') { "Expected #RRGGBB, was '$hex'" }
    return Color(0xFF000000 or hex.substring(1).toLong(16))
}

/** Readable content colour on [background]: dark ink on light colours, white on dark ones. */
internal fun contentColorOn(background: Color): Color =
    if (background.luminance() > 0.5f) Palette.Ink else Palette.White

/** The soft tint of a status colour used behind it ("container"), for light or dark surfaces. */
internal fun containerOf(color: Color, dark: Boolean): Color =
    if (dark) lerp(color, Palette.Night, 0.7f) else lerp(color, Palette.White, 0.88f)

/** Text/icon colour placed on [containerOf] — the base colour pushed toward the surface's ink. */
internal fun onContainerOf(color: Color, dark: Boolean): Color =
    if (dark) lerp(color, Palette.NightInk, 0.5f) else lerp(color, Palette.Ink, 0.45f)

/**
 * Semantic colour roles of the design system. Mirrors the Material 3 slots the app uses and adds the roles
 * Material has no slot for (success / warning / info / neutral with their containers, secondary and tertiary
 * text, input roles). Read anywhere below the theme as `{{THEME_PREFIX}}Theme.colors.<role>`.
 *
 * Add a role here before using a new colour in UI; never hardcode `Color(0x…)` in a screen.
 */
@Immutable
data class {{THEME_PREFIX}}Colors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val neutral: Color,
    val onNeutral: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val outline: Color,
    val outlineStrong: Color,
    val inputFill: Color,
    val inputBorder: Color,
    val inputBorderFocus: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val errorBorder: Color,
    val errorText: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val scrim: Color,
    val isLight: Boolean,
)

/** The light colour set — the only one used unless the theme mode is `light-dark`. */
fun light{{THEME_PREFIX}}Colors(): {{THEME_PREFIX}}Colors = statusColors(dark = false).copy(
    primary = Palette.Brand,
    onPrimary = contentColorOn(Palette.Brand),
    neutral = Palette.Grey,
    onNeutral = Palette.White,
    neutralContainer = Palette.GreyLight,
    onNeutralContainer = Palette.Ink,
    background = Palette.GreyLighter,
    onBackground = Palette.Ink,
    surface = Palette.White,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.GreyLight,
    onSurfaceVariant = Palette.InkSecondary,
    textSecondary = Palette.InkSecondary,
    textTertiary = Palette.Grey,
    outline = Palette.Hairline,
    outlineStrong = Palette.HairlineStrong,
    inputFill = Palette.GreyLighter,
    inputBorder = Palette.HairlineStrong,
    inputBorderFocus = Palette.Brand,
    scrim = Palette.Ink.copy(alpha = 0.4f),
    isLight = true,
)

/** The dark colour set. Same role names; used only when the theme mode is `light-dark`. */
fun dark{{THEME_PREFIX}}Colors(): {{THEME_PREFIX}}Colors = statusColors(dark = true).copy(
    primary = Palette.Brand,
    onPrimary = contentColorOn(Palette.Brand),
    neutral = Palette.Grey,
    onNeutral = Palette.Night,
    neutralContainer = Palette.NightSurfaceVariant,
    onNeutralContainer = Palette.NightInk,
    background = Palette.Night,
    onBackground = Palette.NightInk,
    surface = Palette.NightSurface,
    onSurface = Palette.NightInk,
    surfaceVariant = Palette.NightSurfaceVariant,
    onSurfaceVariant = Palette.NightInkSecondary,
    textSecondary = Palette.NightInkSecondary,
    textTertiary = Palette.Grey,
    outline = Palette.NightHairline,
    outlineStrong = Palette.NightHairlineStrong,
    inputFill = Palette.NightSurfaceVariant,
    inputBorder = Palette.NightHairlineStrong,
    inputBorderFocus = Palette.Brand,
    scrim = Color.Black.copy(alpha = 0.6f),
    isLight = false,
)

/** Status roles and the brand containers, derived from the four answered colours. */
private fun statusColors(dark: Boolean): {{THEME_PREFIX}}Colors {
    val placeholder = Color.Unspecified
    return {{THEME_PREFIX}}Colors(
        primary = placeholder, onPrimary = placeholder,
        primaryContainer = containerOf(Palette.Brand, dark),
        onPrimaryContainer = onContainerOf(Palette.Brand, dark),
        neutral = placeholder, onNeutral = placeholder, neutralContainer = placeholder,
        onNeutralContainer = placeholder,
        background = placeholder, onBackground = placeholder, surface = placeholder, onSurface = placeholder,
        surfaceVariant = placeholder, onSurfaceVariant = placeholder,
        textSecondary = placeholder, textTertiary = placeholder, outline = placeholder, outlineStrong = placeholder,
        inputFill = placeholder, inputBorder = placeholder, inputBorderFocus = placeholder,
        error = Palette.Error,
        onError = contentColorOn(Palette.Error),
        errorContainer = containerOf(Palette.Error, dark),
        onErrorContainer = onContainerOf(Palette.Error, dark),
        errorBorder = Palette.Error,
        errorText = if (dark) lerp(Palette.Error, Palette.White, 0.3f) else Palette.Error,
        success = Palette.Success,
        onSuccess = contentColorOn(Palette.Success),
        successContainer = containerOf(Palette.Success, dark),
        onSuccessContainer = onContainerOf(Palette.Success, dark),
        warning = Palette.Warning,
        onWarning = contentColorOn(Palette.Warning),
        warningContainer = containerOf(Palette.Warning, dark),
        onWarningContainer = onContainerOf(Palette.Warning, dark),
        info = Palette.Brand,
        onInfo = contentColorOn(Palette.Brand),
        infoContainer = containerOf(Palette.Brand, dark),
        onInfoContainer = onContainerOf(Palette.Brand, dark),
        scrim = placeholder,
        isLight = !dark,
    )
}

/**
 * Projects the roles onto a Material 3 [ColorScheme], so stock Material components used anywhere are styled
 * from the same source of truth. Extended roles have no slot and are read from [{{THEME_PREFIX}}Colors] directly.
 */
fun {{THEME_PREFIX}}Colors.toMaterialColorScheme(): ColorScheme {
    val base = if (isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = neutral,
        onSecondary = onNeutral,
        secondaryContainer = neutralContainer,
        onSecondaryContainer = onNeutralContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outline,
        scrim = scrim,
    )
}

/** No default — a missing provider is a programming error and fails loudly. */
val Local{{THEME_PREFIX}}Colors = staticCompositionLocalOf<{{THEME_PREFIX}}Colors> {
    error("No {{THEME_PREFIX}}Colors provided. Wrap content in {{THEME_PREFIX}}Theme { }.")
}
