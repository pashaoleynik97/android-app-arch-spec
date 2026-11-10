package {{ROOT_PACKAGE}}.auth

/** Outcome of [AuthManager.resolveSession], the cold-start check. */
enum class SessionResolution {
    /** A live session is available (a stored one was verified and refreshed). */
    AUTHENTICATED,

    /** No usable session: nothing stored, or the stored one is definitively dead. Go to sign-in. */
    UNAUTHENTICATED,

    /** The check could not complete (offline, timeout, 5xx). The stored session is kept; offer a retry. */
    CONNECTION_ERROR,
}
