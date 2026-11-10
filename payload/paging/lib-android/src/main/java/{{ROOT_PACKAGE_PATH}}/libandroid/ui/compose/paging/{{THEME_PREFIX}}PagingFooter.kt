package {{ROOT_PACKAGE}}.libandroid.ui.compose.paging

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}Button
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}ButtonVariant
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}Icons
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}Loader
import {{ROOT_PACKAGE}}.libandroid.ui.preview.ReferenceWidthPreview
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme
import {{ROOT_PACKAGE}}.libcommon.paging.PagingState

/**
 * The footer a paginated list appends under its loaded rows, chosen from the pager's state:
 * appending → [{{THEME_PREFIX}}PagingLoadingItem]; append failed → [{{THEME_PREFIX}}PagingRetryItem]; otherwise
 * nothing. A **refresh** (first page) failure is not a footer — there is no list to put it under; the screen
 * shows its full-body failure state instead.
 *
 * Texts come from the caller (the feature owns its strings); the look comes from the theme tokens, so a
 * redesign changes this file only.
 *
 * ```
 * LazyColumn(state = listState) {
 *     items(uiState.rows, key = { it.id }) { Row(it) }
 *     pagingFooter(uiState.paging, loadingText, errorText, retryText, onRetry = viewModel::onRetry)
 * }
 * PagingScrollTrigger(listState, viewModel::onLastVisibleIndex)
 * ```
 */
fun LazyListScope.pagingFooter(
    state: PagingState<*, *>,
    loadingText: String,
    errorText: String,
    retryText: String,
    onRetry: () -> Unit,
) {
    when {
        state.isAppending -> item(key = FOOTER_KEY, contentType = FOOTER_KEY) {
            {{THEME_PREFIX}}PagingLoadingItem(text = loadingText)
        }
        state.appendFailure != null -> item(key = FOOTER_KEY, contentType = FOOTER_KEY) {
            {{THEME_PREFIX}}PagingRetryItem(message = errorText, actionText = retryText, onRetry = onRetry)
        }
        else -> Unit
    }
}

/** One stable key for whichever footer is shown, so swapping loading ↔ retry does not jump the scroll position. */
private const val FOOTER_KEY = "paging-footer"

/** "The next page is on its way." A tinted full-width band with the loader and a label. */
@Composable
fun {{THEME_PREFIX}}PagingLoadingItem(
    text: String,
    modifier: Modifier = Modifier,
) {
    val dimens = {{THEME_PREFIX}}Theme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background({{THEME_PREFIX}}Theme.colors.primaryContainer)
            // A minimum, so a translated label may wrap.
            .heightIn(min = dimens.touchTarget)
            .padding(horizontal = dimens.screenEdge, vertical = dimens.spaceSmall),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        {{THEME_PREFIX}}Loader(color = {{THEME_PREFIX}}Theme.colors.primary, diameter = dimens.iconSize)
        Text(
            text = text,
            style = {{THEME_PREFIX}}Theme.typography.bodyMedium,
            color = {{THEME_PREFIX}}Theme.colors.onPrimaryContainer,
            modifier = Modifier.padding(start = dimens.spaceSmall),
        )
    }
}

/**
 * "The next page could not be loaded." A soft error band explaining itself over a retry button — the list above
 * it is untouched and still usable, which is the whole point of separating append from refresh failures.
 */
@Composable
fun {{THEME_PREFIX}}PagingRetryItem(
    message: String,
    actionText: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = {{THEME_PREFIX}}Theme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background({{THEME_PREFIX}}Theme.colors.errorContainer)
            .padding(horizontal = dimens.screenEdge, vertical = dimens.spaceMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall),
    ) {
        Text(
            text = message,
            style = {{THEME_PREFIX}}Theme.typography.bodyMedium,
            color = {{THEME_PREFIX}}Theme.colors.onErrorContainer,
            textAlign = TextAlign.Center,
        )
        {{THEME_PREFIX}}Button(
            text = actionText,
            onClick = onRetry,
            variant = {{THEME_PREFIX}}ButtonVariant.Negative,
            icon = {{THEME_PREFIX}}Icons.Refresh,
        )
    }
}

@ReferenceWidthPreview
@Composable
private fun {{THEME_PREFIX}}PagingLoadingItemPreview() = {{THEME_PREFIX}}Theme {
    {{THEME_PREFIX}}PagingLoadingItem(text = "Loading more…")
}

@ReferenceWidthPreview
@Composable
private fun {{THEME_PREFIX}}PagingRetryItemPreview() = {{THEME_PREFIX}}Theme {
    {{THEME_PREFIX}}PagingRetryItem(message = "Could not load more items", actionText = "Retry", onRetry = {})
}
