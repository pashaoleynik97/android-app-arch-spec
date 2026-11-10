package {{ROOT_PACKAGE}}.auth.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import {{ROOT_PACKAGE}}.auth.AuthState
import {{ROOT_PACKAGE}}.auth.DefaultUserActivityTracker
import {{ROOT_PACKAGE}}.auth.config.ProactiveRefreshConfig
import {{ROOT_PACKAGE}}.auth.internal.DefaultAuthManager
import {{ROOT_PACKAGE}}.auth.support.FakeAuthService
import {{ROOT_PACKAGE}}.auth.support.FakeProfileStore
import {{ROOT_PACKAGE}}.auth.support.FakeSecureTokenStore
import {{ROOT_PACKAGE}}.auth.support.testUser
import {{ROOT_PACKAGE}}.coretesting.FakeTimeSource
import {{ROOT_PACKAGE}}.coretesting.TestDispatcherProvider
import {{ROOT_PACKAGE}}.network.RefreshPolicy
import {{ROOT_PACKAGE}}.network.di.NetworkModule
import {{ROOT_PACKAGE}}.network.log.HttpLogPolicy
import {{ROOT_PACKAGE}}.network.log.HttpLogSettingsRepository
import {{ROOT_PACKAGE}}.network.log.HttpLogSettingsStore
import kotlin.concurrent.thread

/**
 * Integration: the **production** business client ([NetworkModule.provideApiOkHttpClient]) with this module's
 * contributions plugged into its seams, over the real [DefaultAuthManager] on real threads. Proves the DI seam
 * end to end — bearer attached, 401 → one shared refresh → retry, one retry only, background never refreshes.
 */
class TokenAuthenticatorTest {

    private object NoOpLogStore : HttpLogSettingsStore {
        override suspend fun read() = HttpLogPolicy()
        override suspend fun write(policy: HttpLogPolicy) = Unit
    }

    private lateinit var server: MockWebServer
    private lateinit var service: FakeAuthService
    private lateinit var manager: DefaultAuthManager
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        service = FakeAuthService().apply {
            loginHandler = { _, _ -> AuthNetworkResult.Success("access-0", "refresh-0", 300, testUser("alice")) }
            refreshHandler = { AuthNetworkResult.Success("access-1", "refresh-1", 300, user = null) }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val clock = FakeTimeSource()
        manager = DefaultAuthManager(
            authService = service,
            store = FakeSecureTokenStore(),
            profileStore = FakeProfileStore(),
            dispatchers = TestDispatcherProvider(Dispatchers.IO),
            clock = clock,
            activityTracker = DefaultUserActivityTracker(clock),
            proactiveConfig = ProactiveRefreshConfig(enabled = false),
            scope = scope,
        )
        runBlocking {
            scope.coroutineContext.job.children.toList().joinAll() // let the cold-start check finish first
            manager.login("alice", "pw")
        }
        client = NetworkModule.provideApiOkHttpClient(
            interceptors = setOf(AuthInterceptor(manager)),
            authenticators = setOf(TokenAuthenticator(manager)),
            httpLogSettings = HttpLogSettingsRepository(NoOpLogStore, CoroutineScope(Dispatchers.Unconfined)),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** 200 only for [okToken]; 401 otherwise. */
    private fun acceptOnly(okToken: String) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse().setResponseCode(if (request.getHeader("Authorization") == "Bearer $okToken") 200 else 401)
        }
    }

    private fun get(policy: RefreshPolicy? = null): Int = client.newCall(
        Request.Builder().url(server.url("/v1/items")).apply { if (policy != null) tag(RefreshPolicy::class.java, policy) }.build(),
    ).execute().use { it.code }

    @Test
    fun `a 401 refreshes once and retries with the new bearer`() {
        acceptOnly("access-1")

        assertEquals(200, get())
        assertEquals(1, service.refreshCalls)
        assertEquals("access-1", manager.currentAccessToken())
    }

    @Test
    fun `concurrent 401s from several connections share a single refresh`() {
        acceptOnly("access-1")
        val codes = java.util.Collections.synchronizedList(mutableListOf<Int>())

        (1..5).map { thread { codes += get() } }.forEach { it.join() }

        assertTrue(codes.all { it == 200 })
        assertEquals("single-flight", 1, service.refreshCalls)
    }

    @Test
    fun `a fresh token refused at once gives up after one retry and expires the session`() {
        acceptOnly("never")

        assertEquals(401, get())
        assertEquals("one retry, no loop", 2, server.requestCount)
        assertEquals(AuthState.Unauthenticated(AuthState.Reason.SESSION_EXPIRED), manager.authState.value)
    }

    @Test
    fun `a background request never refreshes and never ends the session`() {
        acceptOnly("access-1") // a refresh WOULD make it succeed — only the policy stops it

        assertEquals(401, get(RefreshPolicy.NeverRefresh))
        assertEquals(0, service.refreshCalls)
        assertEquals(AuthState.Authenticated("alice", "access-0"), manager.authState.value)
    }

    @Test
    fun `contrast - the same request marked user-driven refreshes`() {
        acceptOnly("access-1")

        assertEquals(200, get(RefreshPolicy.AllowRefresh))
        assertEquals(1, service.refreshCalls)
    }

    @Test
    fun `a non-auth failure on the retry propagates and keeps the rotated session`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse().setResponseCode(if (request.getHeader("Authorization") == "Bearer access-0") 401 else 500)
        }

        assertEquals(500, get())
        assertEquals(AuthState.Authenticated("alice", "access-1"), manager.authState.value)
    }
}
