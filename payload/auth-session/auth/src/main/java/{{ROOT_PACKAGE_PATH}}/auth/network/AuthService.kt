package {{ROOT_PACKAGE}}.auth.network

import {{ROOT_PACKAGE}}.auth.SessionEndReason
import {{ROOT_PACKAGE}}.auth.SessionUser

/**
 * The session's network seam. The implementation **classifies** every response, so the session state machine
 * never looks at HTTP: whether a failure means "the session is dead" or "the network is flaky" is decided here,
 * and the concurrency tests run against an in-memory fake.
 */
interface AuthService {

    suspend fun login(email: String, password: String): AuthNetworkResult

    suspend fun refresh(refreshToken: String): AuthNetworkResult

    /**
     * Server-side revocation. Any received answer — 2xx or a definitive 4xx — is `true` (done: the endpoint is
     * idempotent); only a transient failure (5xx, IO, timeout) is `false`, which the caller may retry. Never throws.
     */
    suspend fun logout(accessToken: String): Boolean
}

/** Classified outcome of a login or refresh. */
sealed interface AuthNetworkResult {

    /** A usable token pair. [user] is present on login, `null` on refresh. [expiresIn] is the access token's lifetime in seconds. */
    data class Success(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long,
        val user: SessionUser?,
    ) : AuthNetworkResult

    /** A **definitive** refusal: the credentials or the refresh token are dead. The only result that ends a session. */
    data class AuthRejected(
        val code: AuthErrorCode,
        val sessionEndReason: SessionEndReason? = null,
    ) : AuthNetworkResult

    /** Nothing is known (5xx, IO, timeout, unknown 4xx, malformed 2xx). Never ends a session (DATA-17). */
    data class Transient(val cause: Throwable?) : AuthNetworkResult
}
