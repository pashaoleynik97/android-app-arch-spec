package {{ROOT_PACKAGE}}.login.ui.screen

import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import {{ROOT_PACKAGE}}.coretesting.MainDispatcherRule
import {{ROOT_PACKAGE}}.login.domain.LoginFieldError
import {{ROOT_PACKAGE}}.login.domain.LoginInteractor
import {{ROOT_PACKAGE}}.login.domain.SignInOutcome

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Scriptable [LoginInteractor]; [gate] holds a sign-in in flight. */
    private class FakeLoginInteractor : LoginInteractor {
        var result: SignInOutcome = SignInOutcome.Success
        var gate: CompletableDeferred<SignInOutcome>? = null
        val attempts = mutableListOf<Pair<String, String>>()
        override suspend fun signIn(email: String, password: String): SignInOutcome {
            attempts += email to password
            return gate?.await() ?: result
        }
        override suspend fun signOut() = Unit
    }

    private val interactor = FakeLoginInteractor()

    private fun filled() = LoginViewModel(interactor).apply {
        onEmailChange("user@example.test")
        onPasswordChange("password")
    }

    @Test
    fun `submit validates first and sends nothing when invalid`() {
        val vm = LoginViewModel(interactor)

        vm.onSubmit()

        assertEquals(LoginFieldError.Required, vm.uiState.value.emailError)
        assertEquals(LoginFieldError.Required, vm.uiState.value.passwordError)
        assertTrue(interactor.attempts.isEmpty())
    }

    @Test
    fun `editing a field clears only that field's error`() {
        val vm = LoginViewModel(interactor)
        vm.onSubmit()

        vm.onEmailChange("u")

        assertEquals(null, vm.uiState.value.emailError)
        assertEquals(LoginFieldError.Required, vm.uiState.value.passwordError)
    }

    @Test
    fun `a valid form signs in once and stays submitting on success`() {
        val vm = filled()

        vm.onSubmit()

        assertEquals(listOf("user@example.test" to "password"), interactor.attempts)
        assertTrue("the host navigates; the form stays disabled until it does", vm.uiState.value.isSubmitting)
    }

    @Test
    fun `a second submit while one is in flight is ignored`() {
        val vm = filled()
        interactor.gate = CompletableDeferred()

        vm.onSubmit()
        vm.onSubmit()

        assertEquals(1, interactor.attempts.size)
        interactor.gate!!.complete(SignInOutcome.Success)
    }

    @Test
    fun `a refusal shows its notice and clears the password`() {
        val vm = filled()
        interactor.result = SignInOutcome.InvalidCredentials

        vm.onSubmit()

        assertEquals(LoginNotice.InvalidCredentials, vm.uiState.value.notice)
        assertEquals("", vm.password)
        assertEquals(false, vm.uiState.value.isSubmitting)
    }

    @Test
    fun `an unavailable server keeps everything typed so retry is one tap`() {
        val vm = filled()
        interactor.result = SignInOutcome.Unavailable

        vm.onSubmit()

        assertEquals(LoginNotice.Unavailable, vm.uiState.value.notice)
        assertEquals("password", vm.password)
        assertEquals("user@example.test", vm.uiState.value.email)
    }

    @Test
    fun `the published state never contains the password`() {
        val vm = filled()

        assertTrue(!vm.uiState.value.toString().contains("=password"))
        assertEquals(8, vm.uiState.value.passwordLength)
    }
}
