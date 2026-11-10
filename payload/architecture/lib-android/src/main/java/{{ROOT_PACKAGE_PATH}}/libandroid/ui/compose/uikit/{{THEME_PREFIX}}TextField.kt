package {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import {{ROOT_PACKAGE}}.libandroid.ui.preview.ReferenceWidthPreview
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme

private const val DISABLED_ALPHA = 0.6f

/**
 * The one text input of the design system: an optional [label] above a filled, rounded box, and an optional
 * [errorText] below it.
 *
 * **Stateless / unidirectional** — the caller (ultimately the ViewModel) owns [value], [isError] and
 * [enabled] and receives edits through [onValueChange]; state kept inside an input fights the screen's single
 * source of truth.
 *
 * Built on [BasicTextField] rather than Material's `OutlinedTextField` because the design controls the box
 * height, the inner padding and the error border width, which the Material component fixes.
 *
 * States, all from tokens: rest (`inputFill`, 1dp `inputBorder`), focused (`inputBorderFocus`), error
 * (2dp `errorBorder`, message in `errorText`), disabled (dimmed, wins over error — a field the user cannot act
 * on should not shout).
 *
 * @param trailingIcon optional glyph inside the box; clickable only when [onTrailingIconClick] is given (and
 *   the field is enabled). Give it a [trailingIconContentDescription] when it is an action — the password
 *   reveal toggle is the reference use (the caller flips [visualTransformation] and the glyph).
 */
@Composable
fun {{THEME_PREFIX}}TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorText: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    trailingIconContentDescription: String? = null,
) {
    val colors = {{THEME_PREFIX}}Theme.colors
    val dimens = {{THEME_PREFIX}}Theme.dimens
    val typography = {{THEME_PREFIX}}Theme.typography
    val shape = RoundedCornerShape(dimens.radiusSmall)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val showError = enabled && isError
    val fill = if (enabled) colors.inputFill else colors.surfaceVariant
    val borderColor = when {
        showError -> colors.errorBorder
        enabled && focused -> colors.inputBorderFocus
        else -> colors.inputBorder
    }
    val borderWidth = if (showError) dimens.borderWidthEmphasis else dimens.borderWidth

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = dimens.spaceTiny),
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (singleLine) Modifier.height(dimens.inputHeight) else Modifier.heightIn(min = dimens.inputHeight))
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
            enabled = enabled,
            singleLine = singleLine,
            textStyle = typography.bodyLarge.copy(color = if (enabled) colors.onSurface else colors.textTertiary),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            cursorBrush = SolidColor(if (showError) colors.errorBorder else colors.primary),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(fill, shape)
                        .border(borderWidth, borderColor, shape)
                        .padding(horizontal = dimens.spaceMedium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = typography.bodyLarge,
                                color = colors.textTertiary,
                                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                    if (trailingIcon != null) {
                        val clickable = onTrailingIconClick != null && enabled
                        Icon(
                            imageVector = trailingIcon,
                            contentDescription = trailingIconContentDescription,
                            tint = colors.textSecondary,
                            modifier = Modifier
                                .padding(start = dimens.spaceSmall)
                                .then(if (clickable) Modifier.clickable(onClick = onTrailingIconClick) else Modifier)
                                .size(dimens.iconSize),
                        )
                    }
                }
            },
        )

        if (showError && errorText != null) {
            Text(
                text = errorText,
                style = typography.bodySmall,
                color = colors.errorText,
                modifier = Modifier.padding(top = dimens.spaceTiny),
            )
        }
    }
}

@ReferenceWidthPreview
@Composable
private fun TextFieldPreview() = {{THEME_PREFIX}}Theme {
    Column(modifier = Modifier.padding({{THEME_PREFIX}}Theme.dimens.screenEdge)) {
        {{THEME_PREFIX}}TextField(value = "", onValueChange = {}, label = "Name", placeholder = "Your name")
        {{THEME_PREFIX}}TextField(
            value = "abc",
            onValueChange = {},
            label = "Code",
            isError = true,
            errorText = "The code has 6 digits",
            trailingIcon = {{THEME_PREFIX}}Icons.Close,
            onTrailingIconClick = {},
            trailingIconContentDescription = "Clear",
        )
    }
}
