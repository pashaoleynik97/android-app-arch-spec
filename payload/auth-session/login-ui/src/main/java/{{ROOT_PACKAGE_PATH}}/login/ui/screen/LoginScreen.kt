package {{ROOT_PACKAGE}}.login.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar.AppStatusBarConfig
import {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar.SetAppStatusBar
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.SetAppSnackbar
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.TopSnackbarData
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.TopSnackbarStyle
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}Button
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}Icons
import {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit.{{THEME_PREFIX}}TextField
import {{ROOT_PACKAGE}}.libandroid.ui.preview.ReferenceDevicePreview
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme
import {{ROOT_PACKAGE}}.login.domain.LoginFieldError
import {{ROOT_PACKAGE}}.login.ui.R

/** Stateful entry: collects the ViewModel, declares the host chrome, hands plain state to [LoginContent]. */
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val title = stringResource(R.string.login_title)
    val barConfig = remember(title) { AppStatusBarConfig(title = title, onBack = null) }
    SetAppStatusBar(barConfig)
    SetAppSnackbar(
        data = uiState.notice?.toSnackbarData(),
        onClose = viewModel::onNoticeDismissed,
        onDismiss = viewModel::onNoticeDismissed,
    )

    LoginContent(
        uiState = uiState,
        password = viewModel.password,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::onSubmit,
        modifier = modifier,
    )
}

/** Stateless body. Previews call it directly. */
@Composable
private fun LoginContent(
    uiState: LoginUxState,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !uiState.isSubmitting
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding({{THEME_PREFIX}}Theme.dimens.screenEdge),
        verticalArrangement = Arrangement.spacedBy({{THEME_PREFIX}}Theme.dimens.spaceRegular),
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = {{THEME_PREFIX}}Theme.typography.headlineSmall,
            color = {{THEME_PREFIX}}Theme.colors.onBackground,
        )
        {{THEME_PREFIX}}TextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.login_email),
            enabled = enabled,
            isError = uiState.emailError != null,
            errorText = uiState.emailError?.let { stringResource(it.textRes(email = true)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        {{THEME_PREFIX}}TextField(
            value = password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.login_password),
            enabled = enabled,
            isError = uiState.passwordError != null,
            errorText = uiState.passwordError?.let { stringResource(it.textRes(email = false)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        {{THEME_PREFIX}}Button(
            text = stringResource(R.string.login_submit),
            onClick = onSubmit,
            loading = uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun LoginFieldError.textRes(email: Boolean): Int = when (this) {
    LoginFieldError.Required -> R.string.login_error_required
    LoginFieldError.InvalidEmail -> if (email) R.string.login_error_email else R.string.login_error_required
}

@Composable
private fun LoginNotice.toSnackbarData(): TopSnackbarData = TopSnackbarData(
    title = stringResource(
        when (this) {
            LoginNotice.InvalidCredentials -> R.string.login_notice_invalid_credentials
            LoginNotice.AccountDisabled -> R.string.login_notice_account_disabled
            LoginNotice.Refused -> R.string.login_notice_refused
            LoginNotice.Unavailable -> R.string.login_notice_unavailable
        },
    ),
    style = if (this == LoginNotice.Unavailable) TopSnackbarStyle.Warning else TopSnackbarStyle.Error,
    icon = {{THEME_PREFIX}}Icons.AlertTriangle,
    showClose = true,
)

@ReferenceDevicePreview
@Composable
private fun LoginEmptyPreview() = {{THEME_PREFIX}}Theme {
    LoginContent(LoginUxState(), password = "", onEmailChange = {}, onPasswordChange = {}, onSubmit = {})
}

@ReferenceDevicePreview
@Composable
private fun LoginErrorsPreview() = {{THEME_PREFIX}}Theme {
    LoginContent(
        LoginUxState(email = "user", emailError = LoginFieldError.InvalidEmail, passwordError = LoginFieldError.Required),
        password = "",
        onEmailChange = {},
        onPasswordChange = {},
        onSubmit = {},
    )
}

@ReferenceDevicePreview
@Composable
private fun LoginSubmittingPreview() = {{THEME_PREFIX}}Theme {
    LoginContent(LoginUxState(email = "user@example.test", passwordLength = 8, isSubmitting = true), password = "secret12", onEmailChange = {}, onPasswordChange = {}, onSubmit = {})
}
