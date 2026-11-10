package {{ROOT_PACKAGE}}.auth.session

import {{ROOT_PACKAGE}}.auth.AuthState
import {{ROOT_PACKAGE}}.auth.SessionEndReason

/** Why a **live** session ended, as the host tells the user. The host maps each to its text. */
enum class SessionNotice { Expired, Inactivity, SignedInElsewhere, AccessChanged, Ended }

/** Where the session says the user belongs. Navigation is derived from this state, never from one-shot events. */
enum class SessionDestination {
    /** Cold start still resolving: stay where you are. */
    Undecided,

    /** No session: the sign-in screen. */
    SignIn,

    /** A live session: anywhere but the sign-in screen. */
    App,
}

/** What the host should do with its navigation right now. */
enum class SessionRoute { None, ToSignIn, ToStart }

/**
 * The host's session rules, as pure functions so every branch is a JVM test (TEST-20).
 *
 * - **Navigation is state-driven** ([destination] + [route]): applied on every state change *and* every
 *   destination change, it is idempotent and survives activity recreation and process restore — a one-shot
 *   "go to sign-in" event delivered to a dead activity would be lost.
 * - **A notice is raised only on a transition** ([noticeFor]): a live session that dies. A cold start that finds
 *   no session, and an explicit sign-out, return to sign-in silently.
 */
object SessionPolicy {

    fun destination(state: AuthState): SessionDestination = when (state) {
        AuthState.Initializing -> SessionDestination.Undecided
        is AuthState.Authenticated -> SessionDestination.App
        is AuthState.Unauthenticated -> SessionDestination.SignIn
    }

    fun route(destination: SessionDestination, onSignInScreen: Boolean): SessionRoute = when {
        destination == SessionDestination.SignIn && !onSignInScreen -> SessionRoute.ToSignIn
        destination == SessionDestination.App && onSignInScreen -> SessionRoute.ToStart
        else -> SessionRoute.None
    }

    /**
     * The notice for the transition [previous] → [current], or `null` for none. Only `Authenticated →
     * Unauthenticated` can raise one, and not for an explicit sign-out. The backend's precise reason wins when
     * present — even when it maps to "silent" (`LOGOUT`); the coarse reason is the fallback.
     */
    fun noticeFor(previous: AuthState?, current: AuthState): SessionNotice? {
        if (previous !is AuthState.Authenticated || current !is AuthState.Unauthenticated) return null
        if (current.reason == AuthState.Reason.LOGGED_OUT || current.reason == AuthState.Reason.NO_SESSION) return null
        val precise = current.sessionEndReason
        return if (precise != null) precise.toNotice() else current.reason.toNotice()
    }

    private fun SessionEndReason.toNotice(): SessionNotice? = when (this) {
        SessionEndReason.IDLE_TIMEOUT -> SessionNotice.Inactivity
        SessionEndReason.MAX_LIFETIME -> SessionNotice.Expired
        SessionEndReason.NEW_LOGIN -> SessionNotice.SignedInElsewhere
        SessionEndReason.ACCESS_CHANGED, SessionEndReason.PASSWORD_RESET -> SessionNotice.AccessChanged
        SessionEndReason.TOKEN_REUSE, SessionEndReason.UNKNOWN -> SessionNotice.Ended
        SessionEndReason.LOGOUT -> null
    }

    /** Fallback without a precise reason. Inactivity is deliberately unreachable here: only the server decides that. */
    private fun AuthState.Reason.toNotice(): SessionNotice? = when (this) {
        AuthState.Reason.SESSION_EXPIRED -> SessionNotice.Expired
        AuthState.Reason.SESSION_REVOKED -> SessionNotice.Ended
        AuthState.Reason.LOGGED_OUT, AuthState.Reason.NO_SESSION -> null
    }
}
