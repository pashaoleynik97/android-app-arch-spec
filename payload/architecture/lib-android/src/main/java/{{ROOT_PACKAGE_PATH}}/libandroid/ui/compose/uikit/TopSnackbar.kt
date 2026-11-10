package {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import {{ROOT_PACKAGE}}.libandroid.ui.preview.ReferenceWidthPreview
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme

/** Colour role of a banner; each maps to a theme role pair (`info`/`onInfo`, …). */
enum class TopSnackbarStyle { Info, Success, Warning, Error }

/**
 * Immutable description of one top banner — the single source of truth [TopSnackBar] renders.
 *
 * The app-wide UX rules live **on this class** (and are pinned by unit tests), because a rule a call site can
 * forget is a rule that gets forgotten:
 * - [autoDismissMillis]: a banner that offers a close (X) is the user's to close and **never times out**; a
 *   banner without X keeps [durationMillis] (`null` = stays until the owner clears it).
 * - [dismissesOnBodyTap]: when X is the **only** action, a tap anywhere on the banner closes it; with a retry
 *   action as well, only X closes, so a tap aimed at retry never takes the message away.
 */
@Immutable
data class TopSnackbarData(
    val title: String,
    val description: String? = null,
    val style: TopSnackbarStyle = TopSnackbarStyle.Info,
    val icon: ImageVector? = null,
    val showRetry: Boolean = false,
    val showClose: Boolean = false,
    val durationMillis: Long? = DEFAULT_DURATION_MILLIS,
) {
    val autoDismissMillis: Long? get() = durationMillis?.takeIf { !showClose }

    val dismissesOnBodyTap: Boolean get() = showClose && !showRetry

    companion object {
        const val DEFAULT_DURATION_MILLIS = 4_000L
    }
}

private val LeadingIconSize = 24.dp
private val ActionButtonSize = 40.dp
private val ActionIconSize = 18.dp
private val ShadowElevation = 6.dp
private const val DESCRIPTION_ALPHA = 0.85f

/**
 * Overlays an animated [TopSnackBar] on [content], anchored to the top and drawn last (above everything).
 * Placed by the **host** outside the `NavHost`; nesting several hosts layers banners by priority (outermost =
 * highest). Screens never place their own — they raise banners with `SetAppSnackbar`.
 *
 * Data-driven: non-null [data] slides the banner in, `null` slides it out (the last value is kept for the exit
 * animation). When [TopSnackbarData.autoDismissMillis] elapses, [onDismiss] fires so the owner clears [data].
 */
@Composable
fun TopSnackbarHost(
    data: TopSnackbarData?,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()

        var lastData by remember { mutableStateOf<TopSnackbarData?>(null) }
        if (data != null) lastData = data

        val currentOnDismiss by rememberUpdatedState(onDismiss)
        LaunchedEffect(data) {
            val duration = data?.autoDismissMillis ?: return@LaunchedEffect
            delay(duration)
            currentOnDismiss()
        }

        AnimatedVisibility(
            visible = data != null,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            lastData?.let { shown -> TopSnackBar(data = shown, onRetry = onRetry, onClose = onClose) }
        }
    }
}

/**
 * Stateless banner surface: full width, top-anchored, colour bleeding under the status bar while the content is
 * padded below it. Visibility, animation and timing belong to [TopSnackbarHost].
 */
@Composable
fun TopSnackBar(
    data: TopSnackbarData,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    val colors = {{THEME_PREFIX}}Theme.colors
    val dimens = {{THEME_PREFIX}}Theme.dimens
    val (background, content) = when (data.style) {
        TopSnackbarStyle.Info -> colors.info to colors.onInfo
        TopSnackbarStyle.Success -> colors.success to colors.onSuccess
        TopSnackbarStyle.Warning -> colors.warning to colors.onWarning
        TopSnackbarStyle.Error -> colors.error to colors.onError
    }
    val bodyTapSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = ShadowElevation, clip = false)
            .background(background)
            // Above the inset padding, so the whole coloured surface is the target. No ripple: it is a
            // message, and it slides away on the very tap that would play one.
            .then(
                if (data.dismissesOnBodyTap && onClose != null) {
                    Modifier.clickable(interactionSource = bodyTapSource, indication = null, onClick = onClose)
                } else {
                    Modifier
                },
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(dimens.spaceMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (data.icon != null) {
            Icon(imageVector = data.icon, contentDescription = null, tint = content, modifier = Modifier.size(LeadingIconSize))
            Spacer(Modifier.width(dimens.spaceMedium))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.spaceTiny)) {
            Text(text = data.title, style = {{THEME_PREFIX}}Theme.typography.titleSmall, color = content)
            if (data.description != null) {
                Text(
                    text = data.description,
                    style = {{THEME_PREFIX}}Theme.typography.bodyMedium,
                    color = content.copy(alpha = DESCRIPTION_ALPHA),
                )
            }
        }
        if (data.showRetry) {
            Spacer(Modifier.width(dimens.spaceSmall))
            ActionButton({{THEME_PREFIX}}Icons.Refresh, content, onRetry)
        }
        if (data.showClose) {
            Spacer(Modifier.width(dimens.spaceTiny))
            ActionButton({{THEME_PREFIX}}Icons.Close, content, onClose)
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, tint: Color, onClick: (() -> Unit)?) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(ActionButtonSize)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = ActionButtonSize / 2),
                enabled = onClick != null,
                onClick = { onClick?.invoke() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(ActionIconSize))
    }
}

@ReferenceWidthPreview
@Composable
private fun TopSnackBarPreview() = {{THEME_PREFIX}}Theme {
    Column(verticalArrangement = Arrangement.spacedBy({{THEME_PREFIX}}Theme.dimens.spaceSmall)) {
        TopSnackBar(TopSnackbarData("Saved", style = TopSnackbarStyle.Success, icon = {{THEME_PREFIX}}Icons.CheckCircle))
        TopSnackBar(
            TopSnackbarData(
                title = "No connection",
                description = "Changes will sync when you are back online.",
                style = TopSnackbarStyle.Warning,
                icon = {{THEME_PREFIX}}Icons.AlertTriangle,
                showRetry = true,
                showClose = true,
            ),
        )
    }
}
