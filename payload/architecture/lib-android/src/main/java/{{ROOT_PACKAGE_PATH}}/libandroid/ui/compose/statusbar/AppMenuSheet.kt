package {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}Button
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}ButtonContentLayout
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}ButtonVariant
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme

/**
 * The app menu's body: one full-width button per [AppMenuItem]. Tapping an entry closes the menu first
 * ([onClose]) and then runs the entry, so the action never runs under an open sheet.
 */
@Composable
fun AppMenuSheetContent(items: List<AppMenuItem>, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = {{THEME_PREFIX}}Theme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = dimens.screenEdge, vertical = dimens.spaceRegular),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall),
    ) {
        items.forEach { item ->
            {{THEME_PREFIX}}Button(
                text = item.title,
                onClick = {
                    onClose()
                    item.onClick()
                },
                variant = {{THEME_PREFIX}}ButtonVariant.Outline,
                icon = item.icon,
                contentLayout = {{THEME_PREFIX}}ButtonContentLayout.IconStart,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
