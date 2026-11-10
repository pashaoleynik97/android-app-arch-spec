package {{ROOT_PACKAGE}}.auth.support

import kotlinx.coroutines.CompletableDeferred
import {{ROOT_PACKAGE}}.auth.network.AuthNetworkResult
import {{ROOT_PACKAGE}}.auth.network.AuthService
import java.util.concurrent.atomic.AtomicInteger

/**
 * Scriptable [AuthService]: per-call handlers, thread-safe call counters (to prove single-flight), the tokens
 * each refresh presented, and a [refreshGate] that holds a refresh in flight while callers pile up.
 * Defaults mirror the example contract: login answers a user, refresh answers tokens only.
 */
class FakeAuthService : AuthService {

    private val loginCount = AtomicInteger(0)
    private val refreshCount = AtomicInteger(0)
    private val logoutCount = AtomicInteger(0)

    val loginCalls: Int get() = loginCount.get()
    val refreshCalls: Int get() = refreshCount.get()
    val logoutCalls: Int get() = logoutCount.get()

    val refreshTokensSeen: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    val logoutTokensSeen = mutableListOf<String>()

    var loginHandler: (email: String, password: String) -> AuthNetworkResult =
        { email, _ -> AuthNetworkResult.Success("access-$email", "refresh-0", TTL, testUser(email)) }

    var refreshHandler: (refreshToken: String) -> AuthNetworkResult =
        { AuthNetworkResult.Success("access-refreshed", "refresh-next", TTL, user = null) }

    /** When set, every refresh suspends on it before answering. */
    var refreshGate: CompletableDeferred<Unit>? = null

    /** `true` = the server handled it; `false` = transient, the caller may retry. */
    var logoutHandler: (accessToken: String) -> Boolean = { true }

    override suspend fun login(email: String, password: String): AuthNetworkResult {
        loginCount.incrementAndGet()
        return loginHandler(email, password)
    }

    override suspend fun refresh(refreshToken: String): AuthNetworkResult {
        refreshCount.incrementAndGet()
        refreshTokensSeen += refreshToken
        refreshGate?.await()
        return refreshHandler(refreshToken)
    }

    override suspend fun logout(accessToken: String): Boolean {
        logoutCount.incrementAndGet()
        logoutTokensSeen += accessToken
        return logoutHandler(accessToken)
    }

    companion object {
        /** Access-token lifetime the fake hands out, in seconds. */
        const val TTL = 300L
    }
}
