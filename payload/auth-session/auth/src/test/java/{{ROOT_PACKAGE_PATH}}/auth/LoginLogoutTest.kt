package {{ROOT_PACKAGE}}.auth

import app.cash.turbine.test
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import {{ROOT_PACKAGE}}.auth.network.AuthErrorCode
import {{ROOT_PACKAGE}}.auth.network.AuthNetworkResult
import {{ROOT_PACKAGE}}.auth.support.AuthTestEnv
import {{ROOT_PACKAGE}}.auth.support.testUser
import java.io.IOException

class LoginLogoutTest {

    // region login

    @Test
    fun `a successful login stores the refresh token and profile, keeps the access token in memory`() = runTest {
        val env = AuthTestEnv(this)
        env.manager
        advanceUntilIdle()

        env.manager.authState.test {
            assertEquals(AuthState.Unauthenticated(AuthState.Reason.NO_SESSION), awaitItem())
            assertEquals(LoginOutcome.Success, env.manager.login("alice", "pw"))
            assertEquals(AuthState.Authenticated("alice", "access-alice"), awaitItem())
        }
        assertEquals("refresh-0", env.manager.currentRefreshToken())
        assertEquals(testUser("alice"), env.manager.currentUser())
        assertEquals(mapOf("alice" to "refresh-0"), env.store.snapshot())
        assertEquals(mapOf("alice" to testUser("alice")), env.profileStore.snapshot())
    }

    @Test
    fun `refusals map to their outcomes without touching state or storage`() = runTest {
        val env = AuthTestEnv(this)
        env.manager
        advanceUntilIdle()
        val cases = mapOf(
            AuthErrorCode.INVALID_CREDENTIALS to LoginOutcome.InvalidCredentials,
            AuthErrorCode.ACCOUNT_DISABLED to LoginOutcome.AccountDisabled,
            AuthErrorCode.VALIDATION_ERROR to LoginOutcome.Rejected("VALIDATION_ERROR"),
        )

        cases.forEach { (code, expected) ->
            env.service.loginHandler = { _, _ -> AuthNetworkResult.AuthRejected(code) }
            assertEquals(expected, env.manager.login("alice", "pw"))
        }
        assertEquals(AuthState.Unauthenticated(AuthState.Reason.NO_SESSION), env.manager.authState.value)
        assertEquals(0, env.store.saveCount)
    }

    @Test
    fun `a transient failure or a 2xx without a user is unavailable, with no partial write`() = runTest {
        val env = AuthTestEnv(this)
        env.manager
        advanceUntilIdle()

        val cause = IOException("offline")
        env.service.loginHandler = { _, _ -> AuthNetworkResult.Transient(cause) }
        assertEquals(LoginOutcome.Unavailable(cause), env.manager.login("alice", "pw"))

        env.service.loginHandler = { _, _ -> AuthNetworkResult.Success("a", "r", 300, user = null) }
        assertEquals(LoginOutcome.Unavailable(null), env.manager.login("alice", "pw"))

        assertEquals(0, env.store.saveCount)
        assertNull(env.manager.currentAccessToken())
    }

    @Test
    fun `signing in as another user replaces the tokens but keeps both profiles`() = runTest {
        val env = AuthTestEnv(this)
        env.service.loginHandler = { email, _ -> AuthNetworkResult.Success("access-$email", "refresh-$email", 300, testUser(email)) }
        env.manager
        advanceUntilIdle()

        env.manager.login("alice", "pw")
        env.manager.login("bob", "pw")
        advanceUntilIdle()

        assertEquals(mapOf("bob" to "refresh-bob"), env.store.snapshot())
        assertEquals(setOf("alice", "bob"), env.profileStore.snapshot().keys)
        assertEquals("bob", env.manager.currentUserId())
    }

    // endregion

    // region logout

    @Test
    fun `logout wipes tokens and emits LOGGED_OUT, and a second logout emits nothing`() = runTest {
        val env = AuthTestEnv(this)
        env.manager
        advanceUntilIdle()
        env.manager.login("alice", "pw")

        env.manager.authState.test {
            assertEquals(AuthState.Authenticated("alice", "access-alice"), awaitItem())
            launch { env.manager.logout() }
            launch { env.manager.logout() }
            advanceUntilIdle()
            assertEquals(AuthState.Unauthenticated(AuthState.Reason.LOGGED_OUT), awaitItem())
            expectNoEvents()
        }
        assertNull(env.manager.currentAccessToken())
        assertNull(env.manager.currentRefreshToken())
        assertTrue(env.store.snapshot().isEmpty())
        assertEquals("the profile cache survives sign-out", setOf("alice"), env.profileStore.snapshot().keys)
    }

    @Test
    fun `remote revoke posts the access token once and stops on success`() = runTest {
        val env = AuthTestEnv(this)
        env.manager
        advanceUntilIdle()
        env.manager.login("alice", "pw")

        env.manager.revokeRemoteSession()

        assertEquals(listOf("access-alice"), env.service.logoutTokensSeen)
        assertEquals("revoking never touches local state", "access-alice", env.manager.currentAccessToken())
    }

    @Test
    fun `remote revoke retries transient failures and gives up after three attempts`() = runTest {
        val env = AuthTestEnv(this)
        env.service.logoutHandler = { false }
        env.manager
        advanceUntilIdle()
        env.manager.login("alice", "pw")

        env.manager.revokeRemoteSession()

        assertEquals(3, env.service.logoutCalls)
    }

    @Test
    fun `remote revoke without a session does nothing`() = runTest {
        val env = AuthTestEnv(this)
        env.manager
        advanceUntilIdle()

        env.manager.revokeRemoteSession()

        assertEquals(0, env.service.logoutCalls)
    }

    @Test
    fun `ending a live session carries the backend reason, and is a no-op without one`() = runTest {
        val env = AuthTestEnv(this)
        env.manager
        advanceUntilIdle()

        env.manager.endSession(SessionEndReason.ACCESS_CHANGED)
        assertEquals("no live session: nothing to end", AuthState.Unauthenticated(AuthState.Reason.NO_SESSION), env.manager.authState.value)

        env.manager.login("alice", "pw")
        env.manager.endSession(SessionEndReason.ACCESS_CHANGED)

        assertEquals(
            AuthState.Unauthenticated(AuthState.Reason.SESSION_EXPIRED, SessionEndReason.ACCESS_CHANGED),
            env.manager.authState.value,
        )
        assertTrue(env.store.snapshot().isEmpty())
    }

    // endregion
}
