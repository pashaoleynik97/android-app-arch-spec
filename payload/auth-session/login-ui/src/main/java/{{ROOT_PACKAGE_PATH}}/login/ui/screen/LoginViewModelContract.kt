package {{ROOT_PACKAGE}}.login.ui.screen

import kotlinx.coroutines.flow.StateFlow
import {{ROOT_PACKAGE}}.login.domain.LoginFieldError

/** Why the last attempt did not sign in. One slot; the newest replaces the previous. */
enum class LoginNotice { InvalidCredentials, AccountDisabled, Refused, Unavailable }

/** What the sign-in screen renders. Plain data: the composable resolves every string. The password is never in it. */
data class LoginUxState(
    val email: String = "",
    val passwordLength: Int = 0,
    val emailError: LoginFieldError? = null,
    val passwordError: LoginFieldError? = null,
    val isSubmitting: Boolean = false,
    val notice: LoginNotice? = null,
)

/** The screen's surface: state out, intents in. No navigation — the host owns it (see LoginFeatureImpl). */
interface LoginViewModelContract {
    val uiState: StateFlow<LoginUxState>

    /** The password is held by the ViewModel only, never in [uiState] (KERNEL-05); the field reads it from here. */
    val password: String

    fun onEmailChange(value: String)
    fun onPasswordChange(value: String)
    fun onSubmit()
    fun onNoticeDismissed()
}
