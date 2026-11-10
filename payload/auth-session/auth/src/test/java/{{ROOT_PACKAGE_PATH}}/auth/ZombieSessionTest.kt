package {{ROOT_PACKAGE}}.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import {{ROOT_PACKAGE}}.auth.internal.RefreshOutcome
import {{ROOT_PACKAGE}}.auth.network.AuthNetworkResult
import {{ROOT_PACKAGE}}.auth.support.AuthTestEnv
import {{ROOT_PACKAGE}}.auth.support.testUser

/** The generation guard: a late refresh answer never resurrects or overwrites a changed session. */
class ZombieSessionTest {

    @Test
    fun `a refresh completing after logout writes nothing and the state stays LOGGED_OUT`() = runTest {
        val env = AuthTestEnv(this)
        env.service.refreshHandler = { AuthNetworkResult.Success("access-1", "refresh-1", 300, user = null) }
        env.manager
        advanceUntilIdle()
        env.manager.login("alice", "pw")
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        env.service.refreshGate = gate

        val caller = async { env.manager.refresh(seenAccessToken = "access-alice") }
        advanceUntilIdle()
        env.manager.logout()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(RefreshOutcome.Stale, caller.await())
        assertEquals(AuthState.Unauthenticated(AuthState.Reason.LOGGED_OUT), env.manager.authState.value)
        assertTrue(env.store.snapshot().isEmpty())
    }

    @Test
    fun `a refresh for user A does not overwrite a session that switched to user B`() = runTest {
        val env = AuthTestEnv(this)
        env.service.loginHandler = { email, _ -> AuthNetworkResult.Success("access-$email", "refresh-$email", 300, testUser(email)) }
        env.service.refreshHandler = { AuthNetworkResult.Success("access-stale", "refresh-stale", 300, user = null) }
        env.manager
        advanceUntilIdle()
        env.manager.login("alice", "pw")
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        env.service.refreshGate = gate

        val caller = async { env.manager.refresh(seenAccessToken = "access-alice") }
        advanceUntilIdle()
        env.manager.login("bob", "pw")
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(RefreshOutcome.Stale, caller.await())
        assertEquals(AuthState.Authenticated("bob", "access-bob"), env.manager.authState.value)
        assertEquals(mapOf("bob" to "refresh-bob"), env.store.snapshot())
    }

    @Test
    fun `a refresh queued before a login but started after it never spends the new session's token`() = runTest {
        val env = AuthTestEnv(this)
        env.store.seed("alice", "refresh-stored")
        env.service.loginHandler = { _, _ -> AuthNetworkResult.Success("access-new", "refresh-new", 300, testUser("alice")) }
        // The bootstrap refresh is queued by the constructor; the user's login lands before it runs.
        env.manager
        env.manager.login("alice", "pw")
        advanceUntilIdle()

        assertEquals(AuthState.Authenticated("alice", "access-new"), env.manager.authState.value)
        assertTrue("the new session's refresh token was never presented", "refresh-new" !in env.service.refreshTokensSeen)
    }

    @Test
    fun `login, logout and refreshes contending for the lock settle without deadlock`() = runTest {
        val env = AuthTestEnv(this)
        env.manager
        advanceUntilIdle()
        env.manager.login("alice", "pw")
        advanceUntilIdle()

        repeat(10) { launch { env.manager.refresh(seenAccessToken = "access-alice") } }
        launch { env.manager.logout() }
        launch { env.manager.login("alice", "pw") }
        advanceUntilIdle()

        val state = env.manager.authState.value
        assertTrue("a settled state, was $state", state is AuthState.Authenticated || state is AuthState.Unauthenticated)
    }
}
