package {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}Icons
import {{ROOT_PACKAGE}}.libandroid.ui.preview.ReferenceWidthPreview
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme

/**
 * The app bar the host draws once above the `NavHost`: optional back button, title (and subtitle), the
 * screen's actions, then — when the screen declared menu options — the shared menu button. Stateless; the
 * host feeds it from [AppStatusBarController] and [AppMenuController].
 *
 * It pads itself below the system status bar (the activity is edge-to-edge) and paints its surface behind it.
 */
@Composable
fun AppStatusBar(
    config: AppStatusBarConfig,
    modifier: Modifier = Modifier,
    onMenuClick: (() -> Unit)? = null,
    menuContentDescription: String? = null,
) {
    val dimens = {{THEME_PREFIX}}Theme.dimens
    val colors = {{THEME_PREFIX}}Theme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.surface).statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.appBarHeight)
                .padding(horizontal = dimens.spaceTiny),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceTiny),
        ) {
            val onBack = config.onBack
            if (onBack != null) {
                BarIconButton({{THEME_PREFIX}}Icons.ArrowBack, contentDescription = null, onClick = onBack)
            } else {
                Spacer(Modifier.width(dimens.spaceMedium))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.title,
                    style = {{THEME_PREFIX}}Theme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (config.subtitle != null) {
                    Text(
                        text = config.subtitle,
                        style = {{THEME_PREFIX}}Theme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            config.actions.forEach { action ->
                BarIconButton(action.icon, action.contentDescription, action.onClick)
            }
            if (onMenuClick != null) {
                BarIconButton({{THEME_PREFIX}}Icons.Menu, menuContentDescription, onMenuClick)
            }
        }
        HorizontalDivider(thickness = dimens.borderWidth, color = colors.outline)
    }
}

@Composable
private fun BarIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit) {
    val dimens = {{THEME_PREFIX}}Theme.dimens
    Box(
        modifier = Modifier
            .size(dimens.touchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = {{THEME_PREFIX}}Theme.colors.onSurface,
            modifier = Modifier.size(dimens.iconSize),
        )
    }
}

@ReferenceWidthPreview
@Composable
private fun AppStatusBarPreview() = {{THEME_PREFIX}}Theme {
    AppStatusBar(
        config = AppStatusBarConfig(
            title = "Details",
            subtitle = "Item 42",
            onBack = {},
            actions = listOf(StatusBarAction({{THEME_PREFIX}}Icons.Refresh, "Refresh") {}),
        ),
        onMenuClick = {},
    )
}
