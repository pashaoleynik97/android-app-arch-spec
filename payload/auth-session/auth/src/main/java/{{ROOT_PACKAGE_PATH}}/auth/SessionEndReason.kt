package {{ROOT_PACKAGE}}.auth

/**
 * The precise reason the **backend** gives for ending a session — example contract: the 401 body of a refused
 * refresh carries it in `error.details.reason` as the lower-case constant name. Supersedes the coarse
 * [AuthState.Reason] when the host picks a notice. Absent on client-side ends (cold start, explicit logout).
 *
 * An unknown wire value maps to `null` ([fromWire]) and is shown as the neutral "session ended" — a new server
 * reason must never crash the client (DATA-21).
 */
enum class SessionEndReason {
    /** Ended by inactivity (the server's idle window elapsed). */
    IDLE_TIMEOUT,

    /** The session's maximum lifetime was reached. */
    MAX_LIFETIME,

    /** An administrator changed the user's access. */
    ACCESS_CHANGED,

    /** The password was reset. */
    PASSWORD_RESET,

    /** The user signed in on another device. */
    NEW_LOGIN,

    /** The user signed out (elsewhere) — return to sign-in silently. */
    LOGOUT,

    /** Refresh-token reuse was detected — neutral notice, details never disclosed. */
    TOKEN_REUSE,

    /** The server said "ended" without a recognisable cause. */
    UNKNOWN,
    ;

    companion object {
        fun fromWire(reason: String?): SessionEndReason? = entries.firstOrNull { it.name.lowercase() == reason }
    }
}
