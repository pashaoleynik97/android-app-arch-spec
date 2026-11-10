package {{ROOT_PACKAGE}}.auth

import app.cash.turbine.test
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import {{ROOT_PACKAGE}}.auth.internal.RefreshOutcome
import {{ROOT_PACKAGE}}.auth.network.AuthErrorCode
import {{ROOT_PACKAGE}}.auth.network.AuthNetworkResult
import {{ROOT_PACKAGE}}.auth.support.AuthTestEnv
import java.io.IOException

/** Only a definitive refusal ends a session; everything ambiguous keeps it (DATA-17). */
class RefreshFailureClassificationTest {

    private suspend fun TestScope.signedIn(): AuthTestEnv = AuthTestEnv(this).also { env ->
        env.manager
        advanceUntilIdle()
        env.manager.login("alice", "pw")
    }

    @Test
    fun `a refused refresh wipes and emits SESSION_EXPIRED exactly once`() = runTest {
        val env = signedIn()
        env.service.refreshHandler = { AuthNetworkResult.AuthRejected(AuthErrorCode.REFRESH_TOKEN_INVALID) }

        env.manager.authState.test {
            assertEquals(AuthState.Authenticated("alice", "access-alice"), awaitItem())
            assertEquals(RefreshOutcome.AuthFailed, env.manager.refresh("access-alice"))
            advanceUntilIdle()
            assertEquals(AuthState.Unauthenticated(AuthState.Reason.SESSION_EXPIRED), awaitItem())
            env.manager.refresh("access-alice")
            advanceUntilIdle()
            expectNoEvents()
        }
        assertTrue(env.store.snapshot().isEmpty())
    }

    @Test
    fun `a revoked session is told apart from an expired one and carries the precise reason`() = runTest {
        val env = signedIn()
        env.service.refreshHandler = { AuthNetworkResult.AuthRejected(AuthErrorCode.SESSION_REVOKED, SessionEndReason.NEW_LOGIN) }

        env.manager.refresh("access-alice")
        advanceUntilIdle()

        assertEquals(
            AuthState.Unauthenticated(AuthState.Reason.SESSION_REVOKED, SessionEndReason.NEW_LOGIN),
            env.manager.authState.value,
        )
    }

    @Test
    fun `a transient failure keeps the session and a later refresh succeeds`() = runTest {
        val env = signedIn()
        var attempt = 0
        env.service.refreshHandler = {
            if (attempt++ == 0) AuthNetworkResult.Transient(IOException("flaky"))
            else AuthNetworkResult.Success("access-1", "refresh-1", 300, user = null)
        }

        assertTrue(env.manager.refresh("access-alice") is RefreshOutcome.Transient)
        assertEquals(AuthState.Authenticated("alice", "access-alice"), env.manager.authState.value)
        assertEquals("refresh-0", env.manager.currentRefreshToken())

        assertEquals(RefreshOutcome.Refreshed("access-1"), env.manager.refresh("access-alice"))
        assertEquals("identity survives rotation", AuthState.Authenticated("alice", "access-1"), env.manager.authState.value)
    }

    @Test
    fun `refreshing with nothing to refresh with ends the session without crashing`() = runTest {
        val env = AuthTestEnv(this)
        env.manager
        advanceUntilIdle()

        assertEquals(RefreshOutcome.AuthFailed, env.manager.refresh(seenAccessToken = null))
        assertEquals(AuthState.Unauthenticated(AuthState.Reason.SESSION_EXPIRED), env.manager.authState.value)
    }

    @Test
    fun `expiring is ignored when the session already moved past the caller's token`() = runTest {
        val env = signedIn()
        env.service.refreshHandler = { AuthNetworkResult.Success("access-1", "refresh-1", 300, user = null) }
        env.manager.refresh("access-alice")

        env.manager.expireSession(seenAccessToken = "access-alice")
        assertEquals("a stale caller cannot kill the rotated session", AuthState.Authenticated("alice", "access-1"), env.manager.authState.value)

        env.manager.expireSession(seenAccessToken = "access-1")
        assertEquals(AuthState.Unauthenticated(AuthState.Reason.SESSION_EXPIRED), env.manager.authState.value)
    }
}
