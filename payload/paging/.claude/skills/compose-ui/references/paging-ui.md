# Paging UI — scroll trigger and footer items (component: paging)

The Compose half of the `paging` component, in `:lib-android` (package `{{ROOT_PACKAGE}}.libandroid.ui.compose.paging`).
The engine (`Pager`, `PagingState`) is described in the application-architecture skill's `paging.md`. Spec:
`.arch-spec/spec/specs/recommended/paging.md` (PAGE-11, PAGE-12).

## Pieces

| API | Use |
|---|---|
| `PagingScrollTrigger(listState, onLastVisibleIndex)` | reports the last visible index to `Pager.onLastVisibleIndex` (via the VM); draws nothing |
| `LazyListScope.pagingFooter(state, loadingText, errorText, retryText, onRetry)` | the footer item chosen from `PagingState`: loading while appending, retry on append failure, nothing otherwise |
| `{{THEME_PREFIX}}PagingLoadingItem(text)` | tinted band with `{{THEME_PREFIX}}Loader` + label |
| `{{THEME_PREFIX}}PagingRetryItem(message, actionText, onRetry)` | error band with a `{{THEME_PREFIX}}Button` (Negative, refresh icon) |

## Screen shape

```kotlin
val listState = rememberLazyListState()
when {
    paging.refreshFailure != null -> {{THEME_PREFIX}}BlockingMessageScreen(/* failure + retry */)   // not a footer
    paging.isRefreshing && paging.isEmpty -> {{THEME_PREFIX}}LoadingFrame()
    paging.isEmpty && paging.endReached -> EmptyState()
    else -> LazyColumn(state = listState) {
        items(uiState.rows, key = { it.id }) { row -> OrderRow(row) }
        pagingFooter(
            state = paging,
            loadingText = stringResource(R.string.orders_loading_more),
            errorText = stringResource(R.string.orders_load_more_failed),
            retryText = stringResource(R.string.orders_retry),
            onRetry = onRetry,
        )
    }
}
PagingScrollTrigger(listState = listState, onLastVisibleIndex = onLastVisibleIndex)
```

## Rules

**PAGE-11** — The footer is the **last** `LazyColumn` item with one stable key (`pagingFooter` does this); first-page
loading / failure / empty use the screen's normal body states. *Why:* one key keeps the scroll position when the
footer swaps loading ↔ retry; a footer for a first-page failure would sit under an empty list.
<!-- origin: arch-spec PAGE-11 -->

**PAGE-12** — Footer copy comes from the feature's strings; the look comes from theme tokens only. *Why:* features own
their copy; a redesign changes one file.
<!-- origin: arch-spec PAGE-12 -->

- Key rows by their id (`items(rows, key = { it.id })`) — the pager's `itemKey` de-duplication guarantees unique keys.
- Pass the VM's `onLastVisibleIndex` method reference; the trigger keeps the latest lambda without restarting.
- Previews: render the content composable with literal `PagingState`s (appending, append failure, end reached), one
  preview each, at the reference device size.
