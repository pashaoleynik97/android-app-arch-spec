package {{ROOT_PACKAGE}}.libandroid.ui.compose.paging

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * Reports the index of the **last visible row** to [onLastVisibleIndex], which is the only scroll input
 * `{{ROOT_PACKAGE}}.libcommon.paging.Pager` needs — the "how close to the end is close enough" threshold lives in
 * `PagingConfig`, so there is exactly one copy of it and it is unit-tested.
 *
 * Built on `snapshotFlow` + `distinctUntilChanged` rather than a `derivedStateOf` read inside composition: the
 * trigger must fire as a **side effect** of scrolling, not as part of laying out a frame, and it must not do work
 * on every one of the frames a fling produces.
 *
 * Place it anywhere inside the composable that owns [listState]; it draws nothing.
 */
@Composable
fun PagingScrollTrigger(
    listState: LazyListState,
    onLastVisibleIndex: (Int) -> Unit,
) {
    // rememberUpdatedState so a recomposition with a new lambda is picked up without restarting the effect (which
    // would re-emit the current index and re-trigger a load already in flight).
    val latestCallback by rememberUpdatedState(onLastVisibleIndex)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { latestCallback(it) }
    }
}
