package {{ROOT_PACKAGE}}.engineering.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import {{ROOT_PACKAGE}}.engineering.ui.R
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringControl
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringOption
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringRow
import {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar.AppStatusBarConfig
import {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar.SetAppStatusBar
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}BottomSheet
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}Icons
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}TextField
import {{ROOT_PACKAGE}}.libandroid.ui.preview.ReferenceDevicePreview
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme

/**
 * The hidden engineering menu: a pinned search field over scrolling sections. Every row is data contributed by
 * some module (see `EngineeringSection`); this screen only draws rows and forwards taps.
 */
@Composable
fun EngineeringMenuScreen(
    onBack: () -> Unit,
    onNavigate: (route: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EngineeringMenuViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val title = stringResource(R.string.engineering_menu_title)
    val barConfig = remember(title, viewModel) {
        AppStatusBarConfig(title = title, onBack = viewModel::onBackRequested)
    }
    SetAppStatusBar(barConfig)

    val latestOnBack by rememberUpdatedState(onBack)
    val latestOnNavigate by rememberUpdatedState(onNavigate)
    DisposableEffect(viewModel) {
        viewModel.uiCallbacks = object : EngineeringMenuViewModelContract.UiCallbacks {
            override fun goBack() = latestOnBack()
            override fun navigate(route: String) = latestOnNavigate(route)
        }
        onDispose { viewModel.uiCallbacks = null }
    }

    EngineeringMenuContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onQueryClear = viewModel::onQueryClear,
        onRowClick = viewModel::onRowClick,
        onToggleChange = viewModel::onToggleChange,
        modifier = modifier,
    )

    uiState.picker?.let { picker ->
        {{THEME_PREFIX}}BottomSheet(onDismissRequest = viewModel::onPickerDismiss) {
            PickerSheetContent(picker = picker, onSelect = viewModel::onPickerOptionSelected)
        }
    }
}

/** Stateless body. */
@Composable
private fun EngineeringMenuContent(
    uiState: EngineeringMenuUxState,
    onQueryChange: (String) -> Unit,
    onQueryClear: () -> Unit,
    onRowClick: (sectionKey: String, rowKey: String) -> Unit,
    onToggleChange: (sectionKey: String, rowKey: String, checked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = {{THEME_PREFIX}}Theme.dimens
    Column(
        modifier = modifier
            .fillMaxSize()
            .background({{THEME_PREFIX}}Theme.colors.background),
    ) {
        {{THEME_PREFIX}}TextField(
            value = uiState.query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.engineering_menu_search_placeholder),
            trailingIcon = if (uiState.query.isNotEmpty()) {{THEME_PREFIX}}Icons.Close else null,
            onTrailingIconClick = onQueryClear.takeIf { uiState.query.isNotEmpty() },
            trailingIconContentDescription = stringResource(R.string.engineering_menu_search_clear),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.screenEdge, vertical = dimens.spaceSmall),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = dimens.spaceLarge),
        ) {
            if (uiState.isNoMatch) {
                item(key = "no-match") {
                    Text(
                        text = stringResource(R.string.engineering_menu_no_results, uiState.query.trim()),
                        style = {{THEME_PREFIX}}Theme.typography.bodyMedium,
                        color = {{THEME_PREFIX}}Theme.colors.textSecondary,
                        modifier = Modifier.padding(dimens.screenEdge),
                    )
                }
            }
            uiState.sections.forEach { section ->
                item(key = "header-${section.key}") {
                    Text(
                        text = section.title,
                        style = {{THEME_PREFIX}}Theme.typography.titleSmall,
                        color = {{THEME_PREFIX}}Theme.colors.primary,
                        modifier = Modifier.padding(
                            start = dimens.screenEdge,
                            end = dimens.screenEdge,
                            top = dimens.spaceRegular,
                            bottom = dimens.spaceTiny,
                        ),
                    )
                }
                items(items = section.rows, key = { "${section.key}/${it.key}" }) { row ->
                    EngineeringRowItem(
                        row = row,
                        onClick = { onRowClick(section.key, row.key) },
                        onToggleChange = { checked -> onToggleChange(section.key, row.key, checked) },
                    )
                }
            }
        }
    }
}

/** One row, drawn from the control it carries. Disabled rows are dimmed and unresponsive (a real disabled state). */
@Composable
private fun EngineeringRowItem(
    row: EngineeringRow,
    onClick: () -> Unit,
    onToggleChange: (Boolean) -> Unit,
) {
    val dimens = {{THEME_PREFIX}}Theme.dimens
    val actionable = row.enabled && row.control != EngineeringControl.None
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.touchTarget)
            .then(if (actionable) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (row.enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = dimens.screenEdge, vertical = dimens.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = {{THEME_PREFIX}}Theme.typography.bodyLarge,
                color = {{THEME_PREFIX}}Theme.colors.onBackground,
            )
            if (row.description.isNotEmpty()) {
                Text(
                    text = row.description,
                    style = {{THEME_PREFIX}}Theme.typography.bodySmall,
                    color = {{THEME_PREFIX}}Theme.colors.textSecondary,
                )
            }
        }
        when (val control = row.control) {
            EngineeringControl.None -> Unit
            // No chevron on a disabled row: an affordance the row will not honour reads as broken.
            is EngineeringControl.Navigate, is EngineeringControl.Picker -> if (row.enabled) Chevron()
            is EngineeringControl.Toggle -> Switch(
                checked = control.checked,
                onCheckedChange = onToggleChange,
                enabled = row.enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = {{THEME_PREFIX}}Theme.colors.primary,
                    checkedThumbColor = {{THEME_PREFIX}}Theme.colors.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun PickerSheetContent(picker: EngineeringPickerSheet, onSelect: (String) -> Unit) {
    val dimens = {{THEME_PREFIX}}Theme.dimens
    Column(modifier = Modifier.padding(bottom = dimens.spaceLarge)) {
        Text(
            text = picker.title,
            style = {{THEME_PREFIX}}Theme.typography.titleMedium,
            color = {{THEME_PREFIX}}Theme.colors.onSurface,
            modifier = Modifier.padding(horizontal = dimens.screenEdge, vertical = dimens.spaceSmall),
        )
        picker.options.forEach { option -> PickerOptionRow(option, option.key == picker.selectedKey, onSelect) }
    }
}

@Composable
private fun PickerOptionRow(option: EngineeringOption, selected: Boolean, onSelect: (String) -> Unit) {
    val dimens = {{THEME_PREFIX}}Theme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.touchTarget)
            .clickable { onSelect(option.key) }
            .padding(horizontal = dimens.screenEdge, vertical = dimens.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(option.label, style = {{THEME_PREFIX}}Theme.typography.bodyLarge, color = {{THEME_PREFIX}}Theme.colors.onSurface)
            if (option.description.isNotEmpty()) {
                Text(
                    option.description,
                    style = {{THEME_PREFIX}}Theme.typography.bodySmall,
                    color = {{THEME_PREFIX}}Theme.colors.textSecondary,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = {{THEME_PREFIX}}Icons.CheckCircle,
                contentDescription = null,
                tint = {{THEME_PREFIX}}Theme.colors.success,
                modifier = Modifier.size(dimens.iconSize),
            )
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = ChevronRight,
        contentDescription = null,
        tint = {{THEME_PREFIX}}Theme.colors.textSecondary,
        modifier = Modifier.size({{THEME_PREFIX}}Theme.dimens.iconSize),
    )
}

private const val DISABLED_ALPHA = 0.4f

/** Local glyph: the kit's icon set has no chevron, and this module is the only user. */
private val ChevronRight: ImageVector = ImageVector.Builder(
    name = "ChevronRight",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(9f, 6f)
        lineTo(15f, 12f)
        lineTo(9f, 18f)
    }
}.build()

@ReferenceDevicePreview
@Composable
private fun EngineeringMenuPreview() = {{THEME_PREFIX}}Theme {
    EngineeringMenuContent(
        uiState = EngineeringMenuUxState(
            sections = listOf(
                EngineeringMenuSection(
                    key = "device",
                    title = "Device",
                    rows = listOf(
                        EngineeringRow("device", "Device", "Example Phone 7"),
                        EngineeringRow("app-version", "App version", "1.4.2-test (1040020)"),
                    ),
                ),
                EngineeringMenuSection(
                    key = "logging",
                    title = "Logging",
                    rows = listOf(
                        EngineeringRow("verbose", "Verbose logs", "Debug builds only", EngineeringControl.Toggle(true) {}),
                        EngineeringRow(
                            key = "limit",
                            title = "Body size limit",
                            description = "Unavailable in release builds",
                            control = EngineeringControl.Picker(emptyList(), null) {},
                            enabled = false,
                        ),
                    ),
                ),
            ),
        ),
        onQueryChange = {},
        onQueryClear = {},
        onRowClick = { _, _ -> },
        onToggleChange = { _, _, _ -> },
    )
}

@ReferenceDevicePreview
@Composable
private fun EngineeringMenuNoMatchPreview() = {{THEME_PREFIX}}Theme {
    EngineeringMenuContent(
        uiState = EngineeringMenuUxState(query = "bluetooth"),
        onQueryChange = {},
        onQueryClear = {},
        onRowClick = { _, _ -> },
        onToggleChange = { _, _, _ -> },
    )
}
