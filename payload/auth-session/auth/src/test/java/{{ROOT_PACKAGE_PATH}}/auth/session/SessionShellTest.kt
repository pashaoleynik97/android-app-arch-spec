package {{ROOT_PACKAGE}}.auth.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import {{ROOT_PACKAGE}}.auth.AuthState
import {{ROOT_PACKAGE}}.auth.SessionEndReason
import {{ROOT_PACKAGE}}.auth.support.FakeAuthManager

class SessionShellTest {

    private val auth = FakeAuthManager()

    private fun TestScope.shell() = SessionShell(auth, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        .also { runCurrent() }

    @Test
    fun `cold start without a session sends to sign-in silently`() = runTest {
        val shell = shell()
        assertEquals(SessionDestination.Undecided, shell.destination.value)

        auth.authState.value = AuthState.Unauthenticated(AuthState.Reason.NO_SESSION)
        runCurrent()

        assertEquals(SessionDestination.SignIn, shell.destination.value)
        assertNull(shell.notice.value)
    }

    @Test
    fun `a live session that dies raises its notice, and the next session clears it`() = runTest {
        val shell = shell()
        auth.authState.value = AuthState.Authenticated("u", "a")
        runCurrent()
        auth.authState.value = AuthState.Unauthenticated(AuthState.Reason.SESSION_REVOKED, SessionEndReason.NEW_LOGIN)
        runCurrent()

        assertEquals(SessionNotice.SignedInElsewhere, shell.notice.value)
        assertEquals(SessionDestination.SignIn, shell.destination.value)

        auth.authState.value = AuthState.Authenticated("u", "b")
        runCurrent()
        assertNull("a new session starts clean", shell.notice.value)
        assertEquals(SessionDestination.App, shell.destination.value)
    }

    @Test
    fun `dismissing clears the notice`() = runTest {
        val shell = shell()
        auth.authState.value = AuthState.Authenticated("u", "a")
        runCurrent()
        auth.authState.value = AuthState.Unauthenticated(AuthState.Reason.SESSION_EXPIRED)
        runCurrent()

        shell.dismissNotice()

        assertNull(shell.notice.value)
    }

    @Test
    fun `an explicit sign-out sends to sign-in without a notice`() = runTest {
        val shell = shell()
        auth.authState.value = AuthState.Authenticated("u", "a")
        runCurrent()
        auth.authState.value = AuthState.Unauthenticated(AuthState.Reason.LOGGED_OUT)
        runCurrent()

        assertNull(shell.notice.value)
        assertEquals(SessionDestination.SignIn, shell.destination.value)
    }
}
