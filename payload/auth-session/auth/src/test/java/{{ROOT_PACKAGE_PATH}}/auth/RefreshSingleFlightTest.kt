package {{ROOT_PACKAGE}}.auth

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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
import {{ROOT_PACKAGE}}.auth.support.testUser

/** Single-flight refresh (TEST-21): N callers, one network refresh, one shared answer. */
class RefreshSingleFlightTest {

    private suspend fun TestScope.signedIn(env: AuthTestEnv, access: String = "access-0", refresh: String = "refresh-0") {
        env.service.loginHandler = { _, _ -> AuthNetworkResult.Success(access, refresh, 300, testUser("alice")) }
        env.manager
        advanceUntilIdle()
        env.manager.login("alice", "pw")
        advanceUntilIdle()
    }

    @Test
    fun `one refresh rotates the token and re-emits Authenticated with it`() = runTest {
        val env = AuthTestEnv(this)
        env.service.refreshHandler = { AuthNetworkResult.Success("access-1", "refresh-1", 300, user = null) }
        signedIn(env)

        env.manager.authState.test {
            assertEquals(AuthState.Authenticated("alice", "access-0"), awaitItem())
            val outcome = env.manager.refresh(seenAccessToken = "access-0")
            advanceUntilIdle()
            assertEquals(RefreshOutcome.Refreshed("access-1"), outcome)
            assertEquals(AuthState.Authenticated("alice", "access-1"), awaitItem())
        }
        assertEquals(1, env.service.refreshCalls)
        assertEquals("refresh-1", env.manager.currentRefreshToken())
        assertEquals(mapOf("alice" to "refresh-1"), env.store.snapshot())
    }

    @Test
    fun `N concurrent callers share exactly one network refresh and get the same token`() = runTest {
        val env = AuthTestEnv(this)
        env.service.refreshHandler = { AuthNetworkResult.Success("access-1", "refresh-1", 300, user = null) }
        signedIn(env)
        val gate = CompletableDeferred<Unit>()
        env.service.refreshGate = gate

        val callers = (1..5).map { async { env.manager.refresh(seenAccessToken = "access-0") } }
        advanceUntilIdle()
        assertEquals("only one refresh may be in flight", 1, env.service.refreshCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(callers.map { it.await() }.all { it == RefreshOutcome.Refreshed("access-1") })
        assertEquals(1, env.service.refreshCalls)
        assertEquals(listOf("refresh-0"), env.service.refreshTokensSeen)
    }

    @Test
    fun `a caller holding a stale token after a rotation gets the fresh one with no second call`() = runTest {
        val env = AuthTestEnv(this)
        env.service.refreshHandler = { AuthNetworkResult.Success("access-1", "refresh-1", 300, user = null) }
        signedIn(env)
        env.manager.refresh(seenAccessToken = "access-0")
        advanceUntilIdle()

        val outcome = env.manager.refresh(seenAccessToken = "access-0")
        advanceUntilIdle()

        assertEquals(RefreshOutcome.Refreshed("access-1"), outcome)
        assertEquals("the in-lock double-check avoids a second refresh", 1, env.service.refreshCalls)
    }

    @Test
    fun `a single-use refresh token under concurrency does not cause a false logout`() = runTest {
        val env = AuthTestEnv(this)
        // The server rotates and refuses a token presented twice — like the fake backend does.
        var valid = "refresh-0"
        var n = 0
        env.service.refreshHandler = { presented ->
            if (presented == valid) {
                valid = "refresh-${++n}"
                AuthNetworkResult.Success("access-$n", valid, 300, user = null)
            } else {
                AuthNetworkResult.AuthRejected(AuthErrorCode.REFRESH_TOKEN_INVALID)
            }
        }
        signedIn(env)
        val gate = CompletableDeferred<Unit>()
        env.service.refreshGate = gate

        val callers = (1..4).map { async { env.manager.refresh(seenAccessToken = "access-0") } }
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, env.service.refreshCalls)
        assertTrue(callers.map { it.await() }.all { it is RefreshOutcome.Refreshed })
        assertTrue(env.manager.authState.value is AuthState.Authenticated)
    }

    @Test
    fun `reading the token during a refresh in flight never blocks`() = runTest {
        val env = AuthTestEnv(this)
        signedIn(env)
        val gate = CompletableDeferred<Unit>()
        env.service.refreshGate = gate

        val caller = async { env.manager.refresh(seenAccessToken = "access-0") }
        advanceUntilIdle()

        assertEquals("lock-free read of the still-current token", "access-0", env.manager.currentAccessToken())
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(caller.await() is RefreshOutcome.Refreshed)
    }

    @Test
    fun `a caller cancelled while the lock is held does not poison the single-flight slot`() = runTest {
        val env = AuthTestEnv(this)
        env.service.refreshHandler = { AuthNetworkResult.Success("access-1", "refresh-1", 300, user = null) }
        signedIn(env)
        val refreshGate = CompletableDeferred<Unit>()
        env.service.refreshGate = refreshGate

        val caller = launch { env.manager.refresh(seenAccessToken = null) }
        advanceUntilIdle() // the shared refresh is on the network; the caller awaits it

        // Someone holds the lock across a suspension — as the wipe does (store.clear is disk + crypto).
        val clearGate = CompletableDeferred<Unit>()
        env.store.clearGate = clearGate
        launch { env.manager.logout() }
        advanceUntilIdle()

        // The awaiting caller is cancelled now — in production, the wipe cancelling the keep-alive that started
        // this very refresh. Its cleanup must still free the slot.
        caller.cancel()
        advanceUntilIdle()
        clearGate.complete(Unit)
        advanceUntilIdle()
        refreshGate.complete(Unit)
        env.service.refreshGate = null
        advanceUntilIdle()

        env.store.clearGate = null
        env.manager.login("alice", "pw")
        advanceUntilIdle()
        val outcome = env.manager.refresh(seenAccessToken = null)
        advanceUntilIdle()

        assertTrue("a new session must refresh for real", outcome is RefreshOutcome.Refreshed)
        assertEquals("the second refresh reached the network", 2, env.service.refreshCalls)
    }
}
