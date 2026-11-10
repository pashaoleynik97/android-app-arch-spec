package {{ROOT_PACKAGE}}.libandroid.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing, sizing and radius tokens. Read as `{{THEME_PREFIX}}Theme.dimens.<token>`; never write a magic `.dp`
 * in a screen.
 *
 * Two tokens with the same value today stay two tokens when they mean different things ([screenEdge] is the
 * gutter, [spaceRegular] a gap) so either can move without dragging the other along. Add the design's
 * off-scale steps here as named tokens when it uses them.
 */
@Immutable
data class {{THEME_PREFIX}}Dimens(
    // Spacing scale (4dp base)
    val spaceTiny: Dp = 4.dp,
    val spaceSmall: Dp = 8.dp,
    val spaceMedium: Dp = 12.dp,
    val spaceRegular: Dp = 16.dp,
    val spaceLarge: Dp = 24.dp,
    val spaceXLarge: Dp = 32.dp,
    // Semantic layout aliases
    val screenEdge: Dp = 16.dp,
    val itemSpacing: Dp = 12.dp,
    val contentSpacing: Dp = 8.dp,
    // Radius scale — build every RoundedCornerShape from these
    val radiusSmall: Dp = 4.dp,
    val radiusMedium: Dp = 8.dp,
    val radiusLarge: Dp = 12.dp,
    val radiusXLarge: Dp = 16.dp,
    // Component metrics
    val borderWidth: Dp = 1.dp,
    val borderWidthEmphasis: Dp = 2.dp,
    val buttonHeight: Dp = 48.dp,
    val buttonHeightLarge: Dp = 56.dp,
    val inputHeight: Dp = 48.dp,
    val appBarHeight: Dp = 56.dp,
    val iconSize: Dp = 24.dp,
    val touchTarget: Dp = 48.dp,
)

/** No default — a missing provider is a programming error and fails loudly. */
val Local{{THEME_PREFIX}}Dimens = staticCompositionLocalOf<{{THEME_PREFIX}}Dimens> {
    error("No {{THEME_PREFIX}}Dimens provided. Wrap content in {{THEME_PREFIX}}Theme { }.")
}
