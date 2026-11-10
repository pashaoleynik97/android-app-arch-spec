package {{ROOT_PACKAGE}}.login.ui.screen

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import {{ROOT_PACKAGE}}.libandroid.base.BaseViewModel
import {{ROOT_PACKAGE}}.login.domain.LoginInteractor
import {{ROOT_PACKAGE}}.login.domain.LoginValidator
import {{ROOT_PACKAGE}}.login.domain.SignInOutcome
import javax.inject.Inject

/**
 * The sign-in form.
 *
 * - Validates **on submit**; a field's error clears when that field changes (DATA-31).
 * - One attempt in flight: a second submit while submitting is ignored (DATA-54).
 * - On success it keeps showing "submitting": the host is already navigating away, and re-enabling the form for
 *   the frame before it leaves would invite a second tap.
 * - The password lives in a private field, never in the published state, and is cleared after a refusal.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val interactor: LoginInteractor,
) : BaseViewModel(), LoginViewModelContract {

    private val mState = MutableStateFlow(LoginUxState())
    override val uiState: StateFlow<LoginUxState> = mState.asStateFlow()

    override var password: String = ""
        private set

    override fun onEmailChange(value: String) {
        mState.value = mState.value.copy(email = value.trim(), emailError = null, notice = null)
    }

    override fun onPasswordChange(value: String) {
        password = value
        mState.value = mState.value.copy(passwordLength = value.length, passwordError = null, notice = null)
    }

    override fun onSubmit() {
        val current = mState.value
        if (current.isSubmitting) return
        val validation = LoginValidator.validate(current.email, password)
        if (!validation.isValid) {
            mState.value = current.copy(emailError = validation.email, passwordError = validation.password)
            return
        }
        mState.value = current.copy(isSubmitting = true, notice = null)
        viewModelScope.launch {
            when (interactor.signIn(current.email, password)) {
                SignInOutcome.Success -> Unit // the host navigates; stay disabled until it does
                SignInOutcome.InvalidCredentials -> refused(LoginNotice.InvalidCredentials)
                SignInOutcome.AccountDisabled -> refused(LoginNotice.AccountDisabled)
                SignInOutcome.Refused -> refused(LoginNotice.Refused)
                // Ambiguous: keep everything the user typed (DATA-17) and let them retry.
                SignInOutcome.Unavailable -> mState.value = mState.value.copy(isSubmitting = false, notice = LoginNotice.Unavailable)
            }
        }
    }

    override fun onNoticeDismissed() {
        mState.value = mState.value.copy(notice = null)
    }

    private fun refused(notice: LoginNotice) {
        password = ""
        mState.value = mState.value.copy(isSubmitting = false, passwordLength = 0, notice = notice)
    }
}
