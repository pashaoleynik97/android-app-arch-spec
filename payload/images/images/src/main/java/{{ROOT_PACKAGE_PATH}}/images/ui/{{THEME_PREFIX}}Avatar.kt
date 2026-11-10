package {{ROOT_PACKAGE}}.images.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import {{ROOT_PACKAGE}}.libandroid.ui.preview.ReferenceWidthPreview
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme

/**
 * A round user avatar: the remote image when there is one and it loads, otherwise the person's [Initials] on a
 * tinted circle. Pass the URL from `FileUrlResolver.fileUrlOrNull(…)` — `null` shows initials without a request.
 */
@Composable
fun {{THEME_PREFIX}}Avatar(
    imageUrl: String?,
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = DefaultAvatarSize,
    contentDescription: String? = name,
) {
    val initials = remember(name) { Initials.of(name) }
    {{THEME_PREFIX}}RemoteImage(
        url = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier.size(size).clip(CircleShape),
        placeholder = { InitialsCircle(initials) },
    )
}

@Composable
private fun InitialsCircle(initials: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background({{THEME_PREFIX}}Theme.colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = {{THEME_PREFIX}}Theme.typography.titleMedium,
            color = {{THEME_PREFIX}}Theme.colors.onPrimaryContainer,
        )
    }
}

/** A minimum touch-target-sized avatar; pass a token-derived size for other uses. */
private val DefaultAvatarSize = 48.dp

@ReferenceWidthPreview
@Composable
private fun {{THEME_PREFIX}}AvatarInitialsPreview() = {{THEME_PREFIX}}Theme {
    {{THEME_PREFIX}}Avatar(imageUrl = null, name = "Ada Lovelace")
}
