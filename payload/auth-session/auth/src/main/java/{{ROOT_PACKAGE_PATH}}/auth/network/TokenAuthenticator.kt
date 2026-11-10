package {{ROOT_PACKAGE}}.auth.network

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import {{ROOT_PACKAGE}}.auth.internal.RefreshCoordinator
import {{ROOT_PACKAGE}}.auth.internal.RefreshOutcome
import {{ROOT_PACKAGE}}.network.RefreshPolicy
import javax.inject.Inject

/**
 * The business client's 401 handler: refresh once, retry once.
 *
 * - OkHttp calls authenticators **concurrently** from several connections and does not single-flight them, so
 *   every call goes through [RefreshCoordinator.refresh], where one shared refresh serves all callers — a
 *   correctness requirement, because a single-use refresh token presented twice is refused (a false logout).
 * - The suspend coordinator is bridged with [runBlocking]: OkHttp invokes authenticators on its own background
 *   threads, never on the main thread.
 * - **One retry**: a 401 on the retried request (the `priorResponse` chain) means the fresh token was refused
 *   at once — the session is expired instead of looping.
 * - A request tagged [RefreshPolicy.NeverRefresh] is left to fail: background work never extends (or ends) a
 *   session (DATA-19).
 */
internal class TokenAuthenticator @Inject constructor(
    private val coordinator: RefreshCoordinator,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.tag(RefreshPolicy::class.java) == RefreshPolicy.NeverRefresh) return null

        val failedToken = response.request.header(AuthInterceptor.HEADER_AUTHORIZATION)
            ?.removePrefix(AuthInterceptor.BEARER_PREFIX)

        if (responseCount(response) >= MAX_ATTEMPTS) {
            runBlocking { coordinator.expireSession(failedToken) }
            return null
        }

        return when (val outcome = runBlocking { coordinator.refresh(failedToken) }) {
            is RefreshOutcome.Refreshed -> response.request.newBuilder()
                .header(AuthInterceptor.HEADER_AUTHORIZATION, AuthInterceptor.BEARER_PREFIX + outcome.accessToken)
                .build()
            // Dead session, flaky network, or the session changed meanwhile: do not retry.
            RefreshOutcome.AuthFailed, RefreshOutcome.Stale, is RefreshOutcome.Transient -> null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
    }
}
