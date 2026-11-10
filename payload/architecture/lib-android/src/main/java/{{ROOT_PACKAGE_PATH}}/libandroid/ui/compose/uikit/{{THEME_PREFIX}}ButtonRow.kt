package {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme

/**
 * Buttons side by side, always sharing one height: the row is as tall as its tallest child and each button
 * stretches to it. Pass each child `Modifier.weight(1f).fillMaxHeight()` (or a fixed width plus
 * `fillMaxHeight()`). Every row of buttons uses this, so a label that wraps never leaves its neighbour shorter.
 */
@Composable
fun {{THEME_PREFIX}}ButtonRow(
    modifier: Modifier = Modifier,
    spacing: Dp = {{THEME_PREFIX}}Theme.dimens.spaceSmall,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
