package {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import {{ROOT_PACKAGE}}.libandroid.ui.preview.ReferenceWidthPreview
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme

/** Colour variants of [{{THEME_PREFIX}}Button]; each maps to theme roles, never to literal colours. */
enum class {{THEME_PREFIX}}ButtonVariant { Primary, Secondary, Negative, Outline }

/** [Medium] is the default for screen actions; [Large] for prominent, isolated actions. */
enum class {{THEME_PREFIX}}ButtonSize { Medium, Large }

/**
 * [Centered]: icon and label centred together. [IconStart]: icon pinned to the leading edge, label centred in
 * the button.
 */
enum class {{THEME_PREFIX}}ButtonContentLayout { Centered, IconStart }

private const val DISABLED_ALPHA = 0.5f
private const val MAX_LABEL_LINES = 2
private val MinLabelFontSize = 12.sp
private val LoaderDiameter = 24.dp

/** A one-word label is held to one line, so past the shrink floor it ellipsizes rather than splits. */
private fun maxLabelLines(text: String): Int = if (text.any(Char::isWhitespace)) MAX_LABEL_LINES else 1

private data class ButtonColors(val background: Color, val content: Color, val outline: Color?)

@Composable
private fun {{THEME_PREFIX}}ButtonVariant.colors(): ButtonColors {
    val colors = {{THEME_PREFIX}}Theme.colors
    return when (this) {
        {{THEME_PREFIX}}ButtonVariant.Primary -> ButtonColors(colors.primary, colors.onPrimary, outline = null)
        {{THEME_PREFIX}}ButtonVariant.Secondary -> ButtonColors(colors.neutralContainer, colors.onNeutralContainer, outline = null)
        {{THEME_PREFIX}}ButtonVariant.Negative -> ButtonColors(colors.error, colors.onError, outline = null)
        {{THEME_PREFIX}}ButtonVariant.Outline -> ButtonColors(colors.surface, colors.primary, outline = colors.primary)
    }
}

/**
 * The one parametrized button of the design system.
 *
 * - **Height** is a `heightIn` minimum from tokens; **width is the caller's** — `Modifier.fillMaxWidth()` for a
 *   full-width action, `Modifier.weight(1f)` inside a [{{THEME_PREFIX}}ButtonRow].
 * - **Word-safe label.** A multi-word label wraps between words onto a second line that still fits the
 *   minimum height (the vertical padding is sized for two lines). A word is never split: the label shrinks in
 *   0.5sp steps (down to 12sp) until every break falls on whitespace; a one-word label stays on one line and
 *   ellipsizes past the floor. Long translated labels are the most visible layout bug on small screens.
 * - [loading] swaps the content for [{{THEME_PREFIX}}Loader] and disables input; [enabled] `false` dims it.
 * - With [{{THEME_PREFIX}}ButtonContentLayout.IconStart] the icon's width is reserved on both sides so the
 *   label stays optically centred and never runs under the icon.
 */
@Composable
fun {{THEME_PREFIX}}Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: {{THEME_PREFIX}}ButtonVariant = {{THEME_PREFIX}}ButtonVariant.Primary,
    size: {{THEME_PREFIX}}ButtonSize = {{THEME_PREFIX}}ButtonSize.Medium,
    icon: ImageVector? = null,
    contentLayout: {{THEME_PREFIX}}ButtonContentLayout = {{THEME_PREFIX}}ButtonContentLayout.Centered,
    enabled: Boolean = true,
    loading: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val dimens = {{THEME_PREFIX}}Theme.dimens
    val buttonColors = variant.colors()
    val shape = RoundedCornerShape(dimens.radiusSmall)
    val interactionSource = remember { MutableInteractionSource() }
    val clickable = enabled && !loading
    val minHeight = if (size == {{THEME_PREFIX}}ButtonSize.Large) dimens.buttonHeightLarge else dimens.buttonHeight

    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .background(buttonColors.background)
            .then(if (buttonColors.outline != null) Modifier.border(dimens.borderWidth, buttonColors.outline, shape) else Modifier)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = clickable,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = dimens.spaceMedium, vertical = dimens.spaceTiny),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = loading, label = "{{THEME_PREFIX}}ButtonLoading") { isLoading ->
            if (isLoading) {
                {{THEME_PREFIX}}Loader(color = buttonColors.content, diameter = LoaderDiameter)
            } else {
                ButtonContent(text, icon, buttonColors.content, contentLayout, size)
            }
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    icon: ImageVector?,
    contentColor: Color,
    contentLayout: {{THEME_PREFIX}}ButtonContentLayout,
    size: {{THEME_PREFIX}}ButtonSize,
) {
    val dimens = {{THEME_PREFIX}}Theme.dimens
    val baseStyle = if (size == {{THEME_PREFIX}}ButtonSize.Large) {
        {{THEME_PREFIX}}Theme.typography.labelLarge
    } else {
        {{THEME_PREFIX}}Theme.typography.titleSmall
    }
    // Two label lines must fit the minimum height: keep the line height at the font size.
    val style = baseStyle.copy(lineHeight = baseStyle.fontSize * 1.25f)
    val autoSize = remember(style.fontSize) {
        WordSafeTextAutoSize(maxFontSize = style.fontSize, minFontSize = MinLabelFontSize)
    }
    val label: @Composable (Modifier) -> Unit = { labelModifier ->
        Text(
            text = text,
            color = contentColor,
            style = style,
            autoSize = autoSize,
            maxLines = maxLabelLines(text),
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = labelModifier,
        )
    }
    val iconContent: @Composable () -> Unit = {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(dimens.iconSize))
        }
    }

    when (contentLayout) {
        {{THEME_PREFIX}}ButtonContentLayout.Centered -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                iconContent()
                Spacer(Modifier.width(dimens.spaceSmall))
            }
            label(Modifier)
        }

        {{THEME_PREFIX}}ButtonContentLayout.IconStart -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconContent()
            label(Modifier.weight(1f).padding(horizontal = dimens.spaceSmall))
            if (icon != null) Spacer(Modifier.width(dimens.iconSize))
        }
    }
}

@ReferenceWidthPreview
@Composable
private fun ButtonPreview() = {{THEME_PREFIX}}Theme {
    Column(
        modifier = Modifier.padding({{THEME_PREFIX}}Theme.dimens.screenEdge),
        verticalArrangement = Arrangement.spacedBy({{THEME_PREFIX}}Theme.dimens.spaceSmall),
    ) {
        {{THEME_PREFIX}}Button("Continue", onClick = {}, modifier = Modifier.fillMaxWidth())
        {{THEME_PREFIX}}Button("Secondary", onClick = {}, variant = {{THEME_PREFIX}}ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
        {{THEME_PREFIX}}Button("Delete", onClick = {}, variant = {{THEME_PREFIX}}ButtonVariant.Negative, modifier = Modifier.fillMaxWidth())
        {{THEME_PREFIX}}Button("Back", onClick = {}, variant = {{THEME_PREFIX}}ButtonVariant.Outline, icon = {{THEME_PREFIX}}Icons.ArrowBack, contentLayout = {{THEME_PREFIX}}ButtonContentLayout.IconStart, modifier = Modifier.fillMaxWidth())
        {{THEME_PREFIX}}Button("Saving", onClick = {}, loading = true, modifier = Modifier.fillMaxWidth())
        {{THEME_PREFIX}}ButtonRow {
            {{THEME_PREFIX}}Button("Cancel", onClick = {}, variant = {{THEME_PREFIX}}ButtonVariant.Secondary, modifier = Modifier.weight(1f))
            {{THEME_PREFIX}}Button("A considerably longer label", onClick = {}, modifier = Modifier.weight(1f))
        }
    }
}
