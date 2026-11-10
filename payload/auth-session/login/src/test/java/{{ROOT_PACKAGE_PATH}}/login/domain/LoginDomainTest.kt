package {{ROOT_PACKAGE}}.login.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import {{ROOT_PACKAGE}}.auth.AuthManager
import {{ROOT_PACKAGE}}.auth.AuthState
import {{ROOT_PACKAGE}}.auth.LoginOutcome
import {{ROOT_PACKAGE}}.auth.SessionEndReason
import {{ROOT_PACKAGE}}.auth.SessionResolution
import {{ROOT_PACKAGE}}.auth.SessionUser

class LoginDomainTest {

    /** Scriptable [AuthManager]: answers [loginResult], records the order of session calls. */
    private class FakeAuthManager : AuthManager {
        var loginResult: LoginOutcome = LoginOutcome.Success
        val calls = mutableListOf<String>()
        override val authState = MutableStateFlow<AuthState>(AuthState.Initializing)
        override suspend fun resolveSession() = SessionResolution.UNAUTHENTICATED
        override suspend fun login(email: String, password: String): LoginOutcome = loginResult.also { calls += "login:$email" }
        override suspend fun logout() {
            calls += "logout"
        }
        override suspend fun endSession(reason: SessionEndReason?) = Unit
        override suspend fun revokeRemoteSession() {
            calls += "revoke"
        }
        override fun currentAccessToken(): String? = null
        override fun currentRefreshToken(): String? = null
        override fun currentUserId(): String? = null
        override fun currentUser(): SessionUser? = null
    }

    private val auth = FakeAuthManager()
    private val interactor = DefaultLoginInteractor(auth)

    @Test
    fun `every session outcome maps to its sign-in outcome`() = runTest {
        val cases = mapOf(
            LoginOutcome.Success to SignInOutcome.Success,
            LoginOutcome.InvalidCredentials to SignInOutcome.InvalidCredentials,
            LoginOutcome.AccountDisabled to SignInOutcome.AccountDisabled,
            LoginOutcome.Rejected("VALIDATION_ERROR") to SignInOutcome.Refused,
            LoginOutcome.Unavailable(null) to SignInOutcome.Unavailable,
        )
        cases.forEach { (session, expected) ->
            auth.loginResult = session
            assertEquals(expected, interactor.signIn("a@b.test", "pw"))
        }
    }

    @Test
    fun `the email is trimmed before it is sent`() = runTest {
        interactor.signIn("  a@b.test ", "pw")

        assertEquals(listOf("login:a@b.test"), auth.calls)
    }

    @Test
    fun `sign-out revokes first and always wipes locally`() = runTest {
        interactor.signOut()

        assertEquals(listOf("revoke", "logout"), auth.calls)
    }

    @Test
    fun `validation flags empty fields and a malformed email`() {
        assertEquals(LoginValidation(LoginFieldError.Required, LoginFieldError.Required), LoginValidator.validate(" ", ""))
        assertEquals(LoginFieldError.InvalidEmail, LoginValidator.validate("no-at-sign", "pw").email)
        assertEquals(LoginFieldError.InvalidEmail, LoginValidator.validate("a@b@c", "pw").email)
        assertTrue(LoginValidator.validate("user@example.test", "pw").isValid)
        assertFalse(LoginValidator.validate("user@example.test", "").isValid)
    }

    @Test
    fun `a password made of spaces is still a password`() {
        assertTrue("passwords are never trimmed or judged", LoginValidator.validate("a@b", "   ").isValid)
    }
}
