package {{ROOT_PACKAGE}}.auth

/**
 * The session, as [AuthManager.authState] publishes it.
 *
 * [Authenticated] is re-emitted on login **and on every rotation**, so a socket layer can
 * `distinctUntilChanged { it.accessToken }` and re-authenticate its connection, while identity-only consumers
 * map to [Authenticated.userId] and dedupe on that.
 */
sealed interface AuthState {

    /** Cold start: storage is being read or the bootstrap refresh is in flight. A transient failure stays here. */
    data object Initializing : AuthState

    data class Authenticated(val userId: String, val accessToken: String) : AuthState

    /**
     * No live session. [reason] is the coarse cause; on a backend-ended session the precise [sessionEndReason]
     * is carried too (else `null`) and, when present, decides which notice the host shows.
     */
    data class Unauthenticated(
        val reason: Reason,
        val sessionEndReason: SessionEndReason? = null,
    ) : AuthState

    enum class Reason {
        /** Cold start with nothing stored. */
        NO_SESSION,

        /** Explicit [AuthManager.logout]. */
        LOGGED_OUT,

        /** The session ended on its own (refresh refused as expired / unknown, the retried call refused again). */
        SESSION_EXPIRED,

        /** The session was deliberately revoked server-side (sign-in elsewhere, access change, token reuse, …). */
        SESSION_REVOKED,
    }
}
