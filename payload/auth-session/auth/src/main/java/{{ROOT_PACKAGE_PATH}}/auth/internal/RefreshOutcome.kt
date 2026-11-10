package {{ROOT_PACKAGE}}.auth.internal

/** Result of [RefreshCoordinator.refresh]. */
internal sealed interface RefreshOutcome {

    /** A fresh access token (just rotated, or already rotated by a peer). */
    data class Refreshed(val accessToken: String) : RefreshOutcome

    /** The session is definitively dead; storage is wiped and the state already says so. */
    data object AuthFailed : RefreshOutcome

    /** Nothing is known; the session is kept. Fail only the request at hand. */
    data class Transient(val cause: Throwable?) : RefreshOutcome

    /** The session changed (sign-out, another user) while the refresh was in flight; nothing was written. */
    data object Stale : RefreshOutcome
}
