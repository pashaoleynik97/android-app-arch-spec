package {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme

/**
 * The design system's modal bottom sheet. Exists as a component for one rule: **it always opens fully
 * expanded.** Material's default stops half-way first, so a tall sheet hides its own last entries until the user
 * drags it up; on a small screen that is most of them. A rule a call site can forget is a rule that gets
 * forgotten, so it lives here.
 *
 * **Stateless** — shown while the screen state says so (`if (uiState.isSheetOpen) {{THEME_PREFIX}}BottomSheet(…)`);
 * [onDismissRequest] fires for back, a scrim tap and a downward drag alike.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun {{THEME_PREFIX}}BottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = {{THEME_PREFIX}}Theme.dimens
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = {{THEME_PREFIX}}Theme.colors.surface,
        shape = RoundedCornerShape(topStart = dimens.radiusXLarge, topEnd = dimens.radiusXLarge),
        content = content,
    )
}
