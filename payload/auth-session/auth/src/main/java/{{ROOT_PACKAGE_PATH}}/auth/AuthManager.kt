package {{ROOT_PACKAGE}}.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * The single source of truth for the session and the only way into it.
 *
 * Every suspend function is **main-safe** (it moves disk, crypto and network to its own dispatcher) and none
 * throws for an expected failure. Reads (`current…()`) are lock-free, so an OkHttp interceptor can call them on
 * every request without ever waiting for a refresh in flight.
 *
 * Refresh is not on this interface on purpose: it happens only through the OkHttp authenticator, the cold-start
 * check and the keep-alive, all of which share one single-flight refresh inside the implementation.
 */
interface AuthManager {

    /** Hot, always has a value, replays the latest to late subscribers. Re-emits [AuthState.Authenticated] on every rotation. */
    val authState: StateFlow<AuthState>

    /**
     * The cold-start / splash check: verifies a stored session (refreshing it) and reports whether the user is
     * signed in, signed out, or whether the check could not complete. Idempotent and safe to retry; a
     * [SessionResolution.CONNECTION_ERROR] keeps the stored session for the retry.
     */
    suspend fun resolveSession(): SessionResolution

    /** Signs in, stores the session and emits [AuthState.Authenticated]. [password] is a credential: never log or keep it. */
    suspend fun login(email: String, password: String): LoginOutcome

    /**
     * Local sign-out: wipes tokens (memory + disk) and emits [AuthState.Unauthenticated] with
     * [AuthState.Reason.LOGGED_OUT]. Idempotent. Keeps the non-secret profile cache. Pair it with
     * [revokeRemoteSession] first for a full sign-out (local-first: the wipe never depends on the network).
     */
    suspend fun logout()

    /**
     * Ends a live session the **backend** has already ended (an access change reported by a 403, a refused
     * call after a fresh refresh), carrying its [reason] so the host can name the cause. Local only — nothing
     * to revoke. A no-op when no session is live.
     */
    suspend fun endSession(reason: SessionEndReason?)

    /**
     * Best-effort server-side revocation with the current access token, retried a few times on transient
     * failures, then given up. Never touches local state and never throws; a no-op without a token.
     */
    suspend fun revokeRemoteSession()

    /** The in-memory access token, or `null` when not authenticated. Lock-free. */
    fun currentAccessToken(): String?

    /** The in-memory refresh token, or `null`. Lock-free. */
    fun currentRefreshToken(): String?

    /** The signed-in user's id (from the login response), or `null`. Lock-free. */
    fun currentUserId(): String?

    /** The signed-in user's profile (login snapshot, warmed from the profile cache on cold start), or `null`. */
    fun currentUser(): SessionUser?
}
