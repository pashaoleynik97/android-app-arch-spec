package {{ROOT_PACKAGE}}.auth.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import {{ROOT_PACKAGE}}.auth.AuthState
import {{ROOT_PACKAGE}}.auth.AuthState.Reason
import {{ROOT_PACKAGE}}.auth.SessionEndReason

class SessionPolicyTest {

    private val live = AuthState.Authenticated("u", "a")
    private fun ended(reason: Reason, precise: SessionEndReason? = null) = AuthState.Unauthenticated(reason, precise)

    // region destination + route (truth table)

    @Test
    fun `destination follows the auth state`() {
        assertEquals(SessionDestination.Undecided, SessionPolicy.destination(AuthState.Initializing))
        assertEquals(SessionDestination.App, SessionPolicy.destination(live))
        assertEquals(SessionDestination.SignIn, SessionPolicy.destination(ended(Reason.NO_SESSION)))
    }

    @Test
    fun `route moves only on a mismatch between destination and screen`() {
        assertEquals(SessionRoute.ToSignIn, SessionPolicy.route(SessionDestination.SignIn, onSignInScreen = false))
        assertEquals(SessionRoute.None, SessionPolicy.route(SessionDestination.SignIn, onSignInScreen = true))
        assertEquals(SessionRoute.ToStart, SessionPolicy.route(SessionDestination.App, onSignInScreen = true))
        assertEquals(SessionRoute.None, SessionPolicy.route(SessionDestination.App, onSignInScreen = false))
        assertEquals("cold start stays put", SessionRoute.None, SessionPolicy.route(SessionDestination.Undecided, onSignInScreen = false))
        assertEquals(SessionRoute.None, SessionPolicy.route(SessionDestination.Undecided, onSignInScreen = true))
    }

    // endregion

    // region notices

    @Test
    fun `only a live session that dies raises a notice`() {
        assertNull("cold start, nothing stored", SessionPolicy.noticeFor(AuthState.Initializing, ended(Reason.SESSION_EXPIRED)))
        assertNull("first emission", SessionPolicy.noticeFor(null, ended(Reason.SESSION_EXPIRED)))
        assertNull("explicit sign-out is silent", SessionPolicy.noticeFor(live, ended(Reason.LOGGED_OUT)))
        assertNull("rotation", SessionPolicy.noticeFor(live, live.copy(accessToken = "b")))
        assertEquals(SessionNotice.Expired, SessionPolicy.noticeFor(live, ended(Reason.SESSION_EXPIRED)))
        assertEquals(SessionNotice.Ended, SessionPolicy.noticeFor(live, ended(Reason.SESSION_REVOKED)))
    }

    @Test
    fun `the precise backend reason wins over the coarse one`() {
        val expected = mapOf(
            SessionEndReason.IDLE_TIMEOUT to SessionNotice.Inactivity,
            SessionEndReason.MAX_LIFETIME to SessionNotice.Expired,
            SessionEndReason.NEW_LOGIN to SessionNotice.SignedInElsewhere,
            SessionEndReason.ACCESS_CHANGED to SessionNotice.AccessChanged,
            SessionEndReason.PASSWORD_RESET to SessionNotice.AccessChanged,
            SessionEndReason.TOKEN_REUSE to SessionNotice.Ended,
            SessionEndReason.UNKNOWN to SessionNotice.Ended,
            SessionEndReason.LOGOUT to null,
        )
        SessionEndReason.entries.forEach { reason ->
            assertEquals(reason.name, expected.getValue(reason), SessionPolicy.noticeFor(live, ended(Reason.SESSION_REVOKED, reason)))
        }
    }

    @Test
    fun `wire reasons map by lower-case name and unknown ones to null`() {
        assertEquals(SessionEndReason.NEW_LOGIN, SessionEndReason.fromWire("new_login"))
        assertNull(SessionEndReason.fromWire("NEW_LOGIN"))
        assertNull(SessionEndReason.fromWire("from_the_future"))
        assertNull(SessionEndReason.fromWire(null))
    }

    // endregion
}
