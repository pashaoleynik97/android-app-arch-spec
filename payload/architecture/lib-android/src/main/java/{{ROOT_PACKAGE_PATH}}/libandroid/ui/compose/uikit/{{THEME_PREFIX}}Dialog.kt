package {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import {{ROOT_PACKAGE}}.libandroid.ui.preview.ReferenceWidthPreview
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme

private val GlyphSize = 56.dp

/**
 * The design system's modal dialog: optional glyph, title, message and a column of full-width [actions], with an
 * optional close button.
 *
 * **Stateless and state-driven**: shown while the screen's state says so
 * (`if (uiState.dialog != null) {{THEME_PREFIX}}Dialog(…)`), so it survives rotation and is visible to tests.
 * [onDismissRequest] is the single "the user backed out" signal — system back, a tap outside and the close
 * button all call it, so a caller cannot handle only some of them.
 *
 * ```
 * {{THEME_PREFIX}}Dialog(title = …, message = …, onDismissRequest = viewModel::onDialogDismissed) {
 *     {{THEME_PREFIX}}Button(cancel, viewModel::onDialogDismissed, Modifier.fillMaxWidth(), variant = Secondary)
 *     {{THEME_PREFIX}}Button(delete, viewModel::onDeleteConfirmed, Modifier.fillMaxWidth(), variant = Negative)
 * }
 * ```
 */
@Composable
fun {{THEME_PREFIX}}Dialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = {{THEME_PREFIX}}Theme.colors.primary,
    showClose: Boolean = true,
    closeContentDescription: String? = null,
    actions: @Composable ColumnScope.() -> Unit,
) {
    val dimens = {{THEME_PREFIX}}Theme.dimens
    val colors = {{THEME_PREFIX}}Theme.colors

    Dialog(
        onDismissRequest = onDismissRequest,
        // The surface is sized from the screen gutters; the platform default width is tuned for large phones.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = modifier.fillMaxWidth().padding(horizontal = dimens.screenEdge)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.radiusLarge))
                    .background(colors.surface)
                    .padding(horizontal = dimens.spaceRegular, vertical = dimens.spaceLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.spaceRegular),
            ) {
                if (icon != null) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(GlyphSize))
                }
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall)) {
                    Text(
                        text = title,
                        style = {{THEME_PREFIX}}Theme.typography.titleLarge,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = message,
                        style = {{THEME_PREFIX}}Theme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall),
                    content = actions,
                )
            }

            if (showClose) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(dimens.spaceSmall)
                        .size(dimens.touchTarget)
                        .clip(CircleShape)
                        .clickable(onClick = onDismissRequest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = {{THEME_PREFIX}}Icons.Close,
                        contentDescription = closeContentDescription,
                        tint = colors.onSurface,
                        modifier = Modifier.size(dimens.iconSize),
                    )
                }
            }
        }
    }
}

@ReferenceWidthPreview
@Composable
private fun DialogPreview() = {{THEME_PREFIX}}Theme {
    {{THEME_PREFIX}}Dialog(
        title = "Discard changes?",
        message = "Your edits will be lost.",
        icon = {{THEME_PREFIX}}Icons.AlertTriangle,
        iconTint = {{THEME_PREFIX}}Theme.colors.warning,
        onDismissRequest = {},
    ) {
        {{THEME_PREFIX}}Button("Keep editing", onClick = {}, modifier = Modifier.fillMaxWidth())
        {{THEME_PREFIX}}Button(
            "Discard",
            onClick = {},
            variant = {{THEME_PREFIX}}ButtonVariant.Negative,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
