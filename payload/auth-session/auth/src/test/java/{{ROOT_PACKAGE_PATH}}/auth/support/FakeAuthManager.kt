package {{ROOT_PACKAGE}}.auth.support

import kotlinx.coroutines.flow.MutableStateFlow
import {{ROOT_PACKAGE}}.auth.AuthManager
import {{ROOT_PACKAGE}}.auth.AuthState
import {{ROOT_PACKAGE}}.auth.LoginOutcome
import {{ROOT_PACKAGE}}.auth.SessionEndReason
import {{ROOT_PACKAGE}}.auth.SessionResolution
import {{ROOT_PACKAGE}}.auth.SessionUser

/** Minimal [AuthManager] whose [authState] the test drives directly; records end-of-session calls. */
class FakeAuthManager(var accessToken: String? = null) : AuthManager {

    override val authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val endedWith = mutableListOf<SessionEndReason?>()

    override suspend fun resolveSession() = SessionResolution.UNAUTHENTICATED
    override suspend fun login(email: String, password: String): LoginOutcome = LoginOutcome.Success
    override suspend fun logout() = Unit
    override suspend fun endSession(reason: SessionEndReason?) {
        endedWith += reason
    }
    override suspend fun revokeRemoteSession() = Unit
    override fun currentAccessToken(): String? = accessToken
    override fun currentRefreshToken(): String? = null
    override fun currentUserId(): String? = null
    override fun currentUser(): SessionUser? = null
}
