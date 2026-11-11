package {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.ui.R
import {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar.AppStatusBarConfig
import {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar.SetAppStatusBar
import {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar.StatusBarAction
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.BlockingAction
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.SetAppSnackbar
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.TopSnackbarData
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.TopSnackbarStyle
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}BlockingMessageScreen
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}Icons
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}Loader
import {{ROOT_PACKAGE}}.libandroid.ui.preview.ReferenceDevicePreview
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme

/**
 * Stateful entry of the {{FEATURE}} screen: collects the ViewModel, declares the host chrome, wires
 * navigation callbacks, then hands plain [{{FEATURE_PASCAL}}UxState] to the stateless [{{FEATURE_PASCAL}}Content].
 *
 * @param onBack `null` when there is nothing to go back to (the screen is the start destination).
 */
@Composable
fun {{FEATURE_PASCAL}}Screen(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: {{FEATURE_PASCAL}}ViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Host chrome: the app bar and the screen banner are declared here, drawn by the host.
    val title = stringResource(R.string.{{FEATURE_PKG}}_title)
    val refreshLabel = stringResource(R.string.{{FEATURE_PKG}}_refresh)
    val hasBack = onBack != null
    val barConfig = remember(title, refreshLabel, hasBack, viewModel) {
        AppStatusBarConfig(
            title = title,
            onBack = if (hasBack) viewModel::onBackRequested else null,
            actions = listOf(StatusBarAction({{THEME_PREFIX}}Icons.Refresh, refreshLabel, viewModel::onRefresh)),
        )
    }
    SetAppStatusBar(barConfig)
    SetAppSnackbar(
        data = (uiState as? {{FEATURE_PASCAL}}UxState.Content)?.notice?.toSnackbarData(),
        onClose = viewModel::onNoticeDismissed,
        onDismiss = viewModel::onNoticeDismissed,
    )

    // Navigation seam: the ViewModel decides, these lambdas (from registerGraph) execute.
    val latestOnBack by rememberUpdatedState(onBack)
    DisposableEffect(viewModel) {
        viewModel.uiCallbacks = object : {{FEATURE_PASCAL}}ViewModelContract.UiCallbacks {
            override fun goBack() {
                latestOnBack?.invoke()
            }
        }
        onDispose { viewModel.uiCallbacks = null }
    }

    {{FEATURE_PASCAL}}Content(uiState = uiState, onRetry = viewModel::onRetry, modifier = modifier)
}

/** Stateless body: renders a [{{FEATURE_PASCAL}}UxState] and forwards intents. Previews call it directly. */
@Composable
private fun {{FEATURE_PASCAL}}Content(
    uiState: {{FEATURE_PASCAL}}UxState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        {{FEATURE_PASCAL}}UxState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            {{THEME_PREFIX}}Loader(color = {{THEME_PREFIX}}Theme.colors.primary)
        }
        {{FEATURE_PASCAL}}UxState.Failure -> {{THEME_PREFIX}}BlockingMessageScreen(
            title = stringResource(R.string.{{FEATURE_PKG}}_failure_title),
            message = stringResource(R.string.{{FEATURE_PKG}}_failure_message),
            actions = listOf(
                BlockingAction(
                    stringResource(R.string.{{FEATURE_PKG}}_retry),
                    onRetry,
                    icon = {{THEME_PREFIX}}Icons.Refresh,
                ),
            ),
            modifier = modifier,
        )
        is {{FEATURE_PASCAL}}UxState.Content -> Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding({{THEME_PREFIX}}Theme.dimens.screenEdge),
            verticalArrangement = Arrangement.spacedBy({{THEME_PREFIX}}Theme.dimens.spaceSmall),
        ) {
            if (uiState.isEmpty) {
                Text(
                    text = stringResource(R.string.{{FEATURE_PKG}}_empty_title),
                    style = {{THEME_PREFIX}}Theme.typography.headlineSmall,
                    color = {{THEME_PREFIX}}Theme.colors.onBackground,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.{{FEATURE_PKG}}_empty_message),
                    style = {{THEME_PREFIX}}Theme.typography.bodyLarge,
                    color = {{THEME_PREFIX}}Theme.colors.textSecondary,
                )
            } else {
                Text(
                    text = stringResource(R.string.{{FEATURE_PKG}}_items_count, uiState.itemCount),
                    style = {{THEME_PREFIX}}Theme.typography.titleMedium,
                    color = {{THEME_PREFIX}}Theme.colors.onBackground,
                )
            }
        }
    }
}

/** Notice → banner mapping stays in the composable layer, so the ViewModel never resolves strings. */
@Composable
private fun {{FEATURE_PASCAL}}Notice.toSnackbarData(): TopSnackbarData = when (this) {
    {{FEATURE_PASCAL}}Notice.RefreshFailed -> TopSnackbarData(
        title = stringResource(R.string.{{FEATURE_PKG}}_refresh_failed),
        style = TopSnackbarStyle.Warning,
        icon = {{THEME_PREFIX}}Icons.AlertTriangle,
        showClose = true,
    )
}

@ReferenceDevicePreview
@Composable
private fun {{FEATURE_PASCAL}}EmptyPreview() = {{THEME_PREFIX}}Theme {
    {{FEATURE_PASCAL}}Content(uiState = {{FEATURE_PASCAL}}UxState.Content(itemCount = 0), onRetry = {})
}

@ReferenceDevicePreview
@Composable
private fun {{FEATURE_PASCAL}}ContentPreview() = {{THEME_PREFIX}}Theme {
    {{FEATURE_PASCAL}}Content(uiState = {{FEATURE_PASCAL}}UxState.Content(itemCount = 12), onRetry = {})
}

@ReferenceDevicePreview
@Composable
private fun {{FEATURE_PASCAL}}FailurePreview() = {{THEME_PREFIX}}Theme {
    {{FEATURE_PASCAL}}Content(uiState = {{FEATURE_PASCAL}}UxState.Failure, onRetry = {})
}
