package {{ROOT_PACKAGE}}.auth

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import {{ROOT_PACKAGE}}.auth.config.ProactiveRefreshConfig
import {{ROOT_PACKAGE}}.auth.network.AuthErrorCode
import {{ROOT_PACKAGE}}.auth.network.AuthNetworkResult
import {{ROOT_PACKAGE}}.auth.support.AuthTestEnv
import {{ROOT_PACKAGE}}.auth.support.FakeAuthService

/**
 * The activity-gated keep-alive (ADR-0009), in virtual time: the clock reads the test scheduler, so
 * `advanceTimeBy` moves `delay`s and clock reads together. Lifetime 300 s, lead 60 s → checkpoint at 240 s.
 */
class ProactiveRefreshTest {

    private val ttlMs = FakeAuthService.TTL * 1_000
    private val leadMs = 60_000L
    private val checkpointMs = ttlMs - leadMs

    private fun TestScope.env() = AuthTestEnv(this, ProactiveRefreshConfig(enabled = true, leadMillis = leadMs)).also { env ->
        var n = 0
        env.service.refreshHandler = { AuthNetworkResult.Success("access-r${++n}", "refresh-r$n", FakeAuthService.TTL, user = null) }
    }

    private suspend fun TestScope.signIn(env: AuthTestEnv) {
        env.manager
        advanceUntilIdle()
        env.manager.login("alice", "pw")
        runCurrent()
    }

    @Test
    fun `an unused app stops refreshing but keeps its session - only the server ends it`() = runTest {
        val env = env()
        signIn(env)

        advanceTimeBy(ttlMs * 3)
        runCurrent()

        assertEquals("an unused app must not be kept alive", 0, env.service.refreshCalls)
        assertEquals(AuthState.Authenticated("alice", "access-alice"), env.manager.authState.value)
    }

    @Test
    fun `interaction before the checkpoint triggers exactly one proactive refresh`() = runTest {
        val env = env()
        signIn(env)

        advanceTimeBy(100_000)
        env.activityTracker.recordInteraction()
        advanceTimeBy(checkpointMs - 100_000 + 5_000) // just past the checkpoint
        runCurrent()

        assertEquals(1, env.service.refreshCalls)
        assertEquals(AuthState.Authenticated("alice", "access-r1"), env.manager.authState.value)
    }

    @Test
    fun `no interaction means no refresh, and the first touch afterwards refreshes at once`() = runTest {
        val env = env()
        signIn(env)
        advanceTimeBy(ttlMs + 60_000)
        runCurrent()
        assertEquals("contrast: nothing refreshed while idle", 0, env.service.refreshCalls)

        env.activityTracker.recordInteraction()
        runCurrent()

        assertEquals("the touch, not a timer, refreshed", 1, env.service.refreshCalls)
        assertEquals(AuthState.Authenticated("alice", "access-r1"), env.manager.authState.value)
    }

    @Test
    fun `an active session survives several cycles`() = runTest {
        val env = env()
        signIn(env)

        repeat(3) {
            advanceTimeBy(1_000) // the gate is strict (>): touch after the last commit
            env.activityTracker.recordInteraction()
            advanceTimeBy(ttlMs)
            runCurrent()
        }

        assertEquals(3, env.service.refreshCalls)
        assertTrue(env.manager.authState.value is AuthState.Authenticated)
    }

    @Test
    fun `a session ended while the keep-alive waits is not revived by a later touch`() = runTest {
        val env = env()
        signIn(env)
        advanceTimeBy(ttlMs)
        env.manager.logout()
        runCurrent()

        env.activityTracker.recordInteraction()
        advanceUntilIdle()

        assertEquals("the waiting loop was cancelled with the session", 0, env.service.refreshCalls)
    }

    @Test
    fun `a transient failure near expiry is retried after the backoff`() = runTest {
        val env = env()
        var attempt = 0
        env.service.refreshHandler = {
            if (attempt++ == 0) {
                AuthNetworkResult.Transient(null)
            } else {
                AuthNetworkResult.Success("access-ok", "refresh-ok", FakeAuthService.TTL, user = null)
            }
        }
        signIn(env)
        env.activityTracker.recordInteraction()
        advanceTimeBy(1_000)
        env.activityTracker.recordInteraction()

        advanceTimeBy(checkpointMs + 1_000)
        runCurrent()
        assertEquals(1, env.service.refreshCalls)

        advanceTimeBy(ProactiveRefreshConfig().retryDelayMillis + 1)
        runCurrent()
        assertEquals(2, env.service.refreshCalls)
        assertEquals(AuthState.Authenticated("alice", "access-ok"), env.manager.authState.value)
    }

    @Test
    fun `an idle session the backend ended is discovered on the next refresh, with its reason`() = runTest {
        val env = env()
        env.service.refreshHandler = { AuthNetworkResult.AuthRejected(AuthErrorCode.SESSION_EXPIRED, SessionEndReason.IDLE_TIMEOUT) }
        signIn(env)

        env.manager.refresh(seenAccessToken = "access-alice")
        advanceUntilIdle()

        assertEquals(
            AuthState.Unauthenticated(AuthState.Reason.SESSION_EXPIRED, SessionEndReason.IDLE_TIMEOUT),
            env.manager.authState.value,
        )
        assertNull(env.manager.currentAccessToken())
    }
}
