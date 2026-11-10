package {{ROOT_PACKAGE}}.login.domain

import {{ROOT_PACKAGE}}.auth.AuthManager
import {{ROOT_PACKAGE}}.auth.LoginOutcome
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import javax.inject.Inject

/** [LoginInteractor] over the session. Main-safe because every [AuthManager] call is. */
class DefaultLoginInteractor @Inject constructor(
    private val authManager: AuthManager,
) : LoginInteractor {

    private val logTag = logTag()

    override suspend fun signIn(email: String, password: String): SignInOutcome =
        when (val outcome = authManager.login(email.trim(), password)) {
            LoginOutcome.Success -> SignInOutcome.Success
            LoginOutcome.InvalidCredentials -> SignInOutcome.InvalidCredentials
            LoginOutcome.AccountDisabled -> SignInOutcome.AccountDisabled
            is LoginOutcome.Rejected -> SignInOutcome.Refused.also {
                Reporter.warn("sign-in refused: ${outcome.code}", where = logTag)
            }
            is LoginOutcome.Unavailable -> SignInOutcome.Unavailable
        }

    override suspend fun signOut() {
        authManager.revokeRemoteSession() // best effort, self-limiting; never throws
        authManager.logout() // always — the local wipe never depends on the network
    }
}
