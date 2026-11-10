package {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import {{ROOT_PACKAGE}}.libandroid.ui.preview.ReferenceDevicePreview
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme

private val GlyphSize = 72.dp

/** One action of a [{{THEME_PREFIX}}BlockingMessageScreen]. */
data class BlockingAction(
    val text: String,
    val onClick: () -> Unit,
    val variant: {{THEME_PREFIX}}ButtonVariant = {{THEME_PREFIX}}ButtonVariant.Primary,
    val icon: ImageVector? = null,
)

/**
 * A full-screen dead end, failure or permission state: a glyph, a title, a message and the actions out of it.
 * The caller owns all copy and actions; the component owns the layout.
 *
 * The whole block (actions included) is one scrolling column, so long copy on a small screen scrolls the
 * actions into view instead of pushing them off-screen. Use it instead of a bare error text in a screen body:
 * a dead end the user cannot act on must say so and offer the way out.
 *
 * @param iconTint `null` draws [icon] untinted (a multicolour illustration).
 */
@Composable
fun {{THEME_PREFIX}}BlockingMessageScreen(
    title: String,
    message: String,
    actions: List<BlockingAction>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = {{THEME_PREFIX}}Icons.AlertTriangle,
    iconTint: Color? = {{THEME_PREFIX}}Theme.colors.warning,
) {
    val dimens = {{THEME_PREFIX}}Theme.dimens
    Column(
        modifier = modifier
            .fillMaxSize()
            .background({{THEME_PREFIX}}Theme.colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenEdge, vertical = dimens.spaceLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: Color.Unspecified,
                modifier = Modifier.size(GlyphSize),
            )
            Spacer(Modifier.height(dimens.spaceRegular))
        }
        Text(
            text = title,
            style = {{THEME_PREFIX}}Theme.typography.headlineSmall,
            color = {{THEME_PREFIX}}Theme.colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(dimens.spaceSmall))
        Text(
            text = message,
            style = {{THEME_PREFIX}}Theme.typography.bodyLarge,
            color = {{THEME_PREFIX}}Theme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(dimens.spaceLarge))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall),
        ) {
            actions.forEach { action ->
                {{THEME_PREFIX}}Button(
                    text = action.text,
                    onClick = action.onClick,
                    variant = action.variant,
                    icon = action.icon,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@ReferenceDevicePreview
@Composable
private fun BlockingMessageScreenPreview() = {{THEME_PREFIX}}Theme {
    {{THEME_PREFIX}}BlockingMessageScreen(
        title = "Nothing to show",
        message = "This content is not available. Try again later.",
        actions = listOf(
            BlockingAction("Try again", onClick = {}, icon = {{THEME_PREFIX}}Icons.Refresh),
            BlockingAction("Back", onClick = {}, variant = {{THEME_PREFIX}}ButtonVariant.Outline),
        ),
    )
}
