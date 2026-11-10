package {{ROOT_PACKAGE}}.auth.access

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import {{ROOT_PACKAGE}}.auth.AuthState
import {{ROOT_PACKAGE}}.auth.SessionEndReason
import {{ROOT_PACKAGE}}.auth.support.FakeAuthManager
import {{ROOT_PACKAGE}}.coretesting.FakeTimeSource
import {{ROOT_PACKAGE}}.network.access.AccessLoss
import {{ROOT_PACKAGE}}.network.access.AccessLossBus
import {{ROOT_PACKAGE}}.network.access.AccessLossConfig
import {{ROOT_PACKAGE}}.network.access.AccessLossHandler
import {{ROOT_PACKAGE}}.network.access.ScopeRecovery

class AccessLossFallbackTest {

    /** Captures the fallback the subject installs. */
    private class CapturingBus : AccessLossBus {
        var installed: AccessLossHandler? = null
        override fun report(loss: AccessLoss) = Unit
        override fun register(handler: AccessLossHandler) = AccessLossBus.Registration {}
        override fun setFallback(handler: AccessLossHandler) {
            installed = handler
        }
    }

    /** Scriptable [ScopeRecovery]: counts calls, answers [result]. */
    private class FakeScopeRecovery(var result: Boolean = true) : ScopeRecovery {
        var calls = 0
        override suspend fun recover(loss: AccessLoss.ScopeLost): Boolean {
            calls++
            return result
        }
    }

    private val bus = CapturingBus()
    private val auth = FakeAuthManager().also { it.authState.value = AuthState.Authenticated("u", "a") }
    private val clock = FakeTimeSource()
    private val scopeLost = AccessLoss.ScopeLost("/v1/items", "SCOPE_ACCESS_REVOKED")

    private fun TestScope.fallback(recoveries: Set<ScopeRecovery> = emptySet()) = AccessLossFallback(
        bus, auth, recoveries, AccessLossConfig(maxRecoveries = 2), clock,
        CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
    ).also { runCurrent() }

    @Test
    fun `it installs itself as the bus fallback and always consumes`() = runTest {
        val subject = fallback()

        assertTrue(bus.installed!!.handle(AccessLoss.Forbidden("/x", null)))
        assertEquals(AccessNotice.Forbidden, subject.notice.value)
    }

    @Test
    fun `a lost scope without a recovery ends the session as an access change`() = runTest {
        fallback()

        bus.installed!!.handle(scopeLost)

        assertEquals(listOf<SessionEndReason?>(SessionEndReason.ACCESS_CHANGED), auth.endedWith)
    }

    @Test
    fun `a bound recovery handles a lost scope until the budget runs out`() = runTest {
        val recovery = FakeScopeRecovery()
        fallback(setOf(recovery))

        repeat(2) { bus.installed!!.handle(scopeLost) }
        assertTrue("within budget: recovered, session kept", auth.endedWith.isEmpty())

        bus.installed!!.handle(scopeLost)
        assertEquals("a third round inside the window is a loop", 2, recovery.calls)
        assertEquals(listOf<SessionEndReason?>(SessionEndReason.ACCESS_CHANGED), auth.endedWith)
    }

    @Test
    fun `a recovery that declines ends the session`() = runTest {
        fallback(setOf(FakeScopeRecovery(result = false)))

        bus.installed!!.handle(scopeLost)

        assertEquals(listOf<SessionEndReason?>(SessionEndReason.ACCESS_CHANGED), auth.endedWith)
    }

    @Test
    fun `a dead session clears the notice`() = runTest {
        val subject = fallback()
        bus.installed!!.handle(AccessLoss.Forbidden("/x", null))

        auth.authState.value = AuthState.Unauthenticated(AuthState.Reason.SESSION_EXPIRED)
        runCurrent()

        assertNull(subject.notice.value)
    }
}
