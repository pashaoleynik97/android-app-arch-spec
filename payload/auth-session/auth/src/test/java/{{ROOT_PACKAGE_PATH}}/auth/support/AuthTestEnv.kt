package {{ROOT_PACKAGE}}.auth.support

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import {{ROOT_PACKAGE}}.auth.DefaultUserActivityTracker
import {{ROOT_PACKAGE}}.auth.config.ProactiveRefreshConfig
import {{ROOT_PACKAGE}}.auth.internal.DefaultAuthManager
import {{ROOT_PACKAGE}}.coretesting.TestDispatcherProvider
import {{ROOT_PACKAGE}}.libcommon.time.TimeSource

/** A [TimeSource] reading the test scheduler's virtual clock, so `delay` and clock reads always agree. */
class SchedulerTimeSource(private val testScope: TestScope) : TimeSource {
    override fun elapsedRealtimeMillis(): Long = testScope.testScheduler.currentTime
}

/**
 * Wires the real [DefaultAuthManager] over in-memory fakes on one test dispatcher (TEST-16).
 *
 * Seed [store] and script [service] **before** touching [manager]: it is built lazily, and its constructor
 * launches the cold-start check. The manager's scope is a plain scope on the test scheduler (not
 * `backgroundScope`), so `advanceUntilIdle` drives the bootstrap and the shared refresh; `SupervisorJob` mirrors
 * production. The keep-alive is **off** unless a test passes a config that enables it.
 */
internal class AuthTestEnv(
    testScope: TestScope,
    private val proactiveConfig: ProactiveRefreshConfig = ProactiveRefreshConfig(enabled = false),
) {
    val store = FakeSecureTokenStore()
    val profileStore = FakeProfileStore()
    val service = FakeAuthService()

    private val dispatcher = StandardTestDispatcher(testScope.testScheduler)
    val clock: TimeSource = SchedulerTimeSource(testScope)
    val activityTracker = DefaultUserActivityTracker(clock)
    val scope = CoroutineScope(SupervisorJob() + dispatcher)

    val manager: DefaultAuthManager by lazy {
        DefaultAuthManager(
            authService = service,
            store = store,
            profileStore = profileStore,
            dispatchers = TestDispatcherProvider(dispatcher),
            clock = clock,
            activityTracker = activityTracker,
            proactiveConfig = proactiveConfig,
            scope = scope,
        )
    }
}
