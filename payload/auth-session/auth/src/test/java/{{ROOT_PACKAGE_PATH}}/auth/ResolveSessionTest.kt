package {{ROOT_PACKAGE}}.auth

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import {{ROOT_PACKAGE}}.auth.network.AuthErrorCode
import {{ROOT_PACKAGE}}.auth.network.AuthNetworkResult
import {{ROOT_PACKAGE}}.auth.support.AuthTestEnv
import {{ROOT_PACKAGE}}.auth.support.testUser
import java.io.IOException

/** The cold-start check: no session, verified session, dead session, and the never-a-false-logout transient path. */
class ResolveSessionTest {

    @Test
    fun `nothing stored resolves UNAUTHENTICATED with no network call`() = runTest {
        val env = AuthTestEnv(this)

        assertEquals(SessionResolution.UNAUTHENTICATED, env.manager.resolveSession())
        advanceUntilIdle()

        assertEquals(0, env.service.refreshCalls)
        assertEquals(AuthState.Unauthenticated(AuthState.Reason.NO_SESSION), env.manager.authState.value)
    }

    @Test
    fun `a stored session that refreshes resolves AUTHENTICATED with the cached profile`() = runTest {
        val env = AuthTestEnv(this)
        env.store.seed("alice", "refresh-alice")
        env.profileStore.seed(testUser("alice"))
        env.service.refreshHandler = { AuthNetworkResult.Success("access-new", "refresh-next", 300, user = null) }

        assertEquals(SessionResolution.AUTHENTICATED, env.manager.resolveSession())
        advanceUntilIdle()

        assertEquals(AuthState.Authenticated("alice", "access-new"), env.manager.authState.value)
        assertEquals(testUser("alice"), env.manager.currentUser())
        assertEquals("cold start and the explicit call share one refresh", 1, env.service.refreshCalls)
    }

    @Test
    fun `a stored but refused session resolves UNAUTHENTICATED and wipes`() = runTest {
        val env = AuthTestEnv(this)
        env.store.seed("alice", "refresh-alice")
        env.service.refreshHandler = { AuthNetworkResult.AuthRejected(AuthErrorCode.REFRESH_TOKEN_INVALID) }

        assertEquals(SessionResolution.UNAUTHENTICATED, env.manager.resolveSession())
        advanceUntilIdle()

        assertEquals(AuthState.Unauthenticated(AuthState.Reason.SESSION_EXPIRED), env.manager.authState.value)
        assertTrue(env.store.snapshot().isEmpty())
    }

    @Test
    fun `a transient failure resolves CONNECTION_ERROR, keeps everything, and a retry succeeds`() = runTest {
        val env = AuthTestEnv(this)
        env.store.seed("alice", "refresh-alice")
        var attempt = 0
        env.service.refreshHandler = {
            if (attempt++ == 0) {
                AuthNetworkResult.Transient(IOException("offline"))
            } else {
                AuthNetworkResult.Success("access-new", "refresh-next", 300, user = null)
            }
        }

        assertEquals(SessionResolution.CONNECTION_ERROR, env.manager.resolveSession())
        advanceUntilIdle()
        assertEquals("never a false sign-out on a flaky network", AuthState.Initializing, env.manager.authState.value)
        assertTrue(env.store.snapshot().isNotEmpty())

        assertEquals(SessionResolution.AUTHENTICATED, env.manager.resolveSession())
        assertEquals(AuthState.Authenticated("alice", "access-new"), env.manager.authState.value)
    }

    @Test
    fun `an undecryptable stored token degrades to a dead session without crashing`() = runTest {
        val env = AuthTestEnv(this)
        env.store.seed("alice", "refresh-alice")
        env.store.failReads = true

        env.manager
        advanceUntilIdle()

        assertEquals(AuthState.Unauthenticated(AuthState.Reason.SESSION_EXPIRED), env.manager.authState.value)
        assertTrue(env.store.snapshot().isEmpty())
    }

    @Test
    fun `resolving again once authenticated does not refresh again`() = runTest {
        val env = AuthTestEnv(this)
        env.store.seed("alice", "refresh-alice")
        env.manager
        advanceUntilIdle()
        val calls = env.service.refreshCalls

        assertEquals(SessionResolution.AUTHENTICATED, env.manager.resolveSession())
        advanceUntilIdle()

        assertEquals(calls, env.service.refreshCalls)
    }
}
