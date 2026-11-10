package {{ROOT_PACKAGE}}.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import {{ROOT_PACKAGE}}.network.di.NetworkModule
import {{ROOT_PACKAGE}}.network.log.HttpLogPolicy
import {{ROOT_PACKAGE}}.network.log.HttpLogSettingsRepository
import {{ROOT_PACKAGE}}.network.log.HttpLogSettingsStore

/**
 * The business client as production builds it ([NetworkModule.provideApiOkHttpClient]), with the extension
 * seams filled by test contributors: it must work with none, order the contributed interceptors, and use the
 * one contributed authenticator.
 */
class ApiClientTest {

    private object NoOpStore : HttpLogSettingsStore {
        override suspend fun read() = HttpLogPolicy()
        override suspend fun write(policy: HttpLogPolicy) = Unit
    }

    /** Appends its [name] to the `X-Trace` header, so the server sees the order interceptors ran in. */
    private class TracingInterceptor(override val order: Int, private val name: String) : ApiInterceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val trace = listOfNotNull(chain.request().header("X-Trace"), name).joinToString(",")
            return chain.proceed(chain.request().newBuilder().header("X-Trace", trace).build())
        }
    }

    /** Retries a 401 once with a fixed header; counts calls. */
    private class RecordingAuthenticator : Authenticator {
        var calls = 0
        override fun authenticate(route: okhttp3.Route?, response: Response): Request? {
            calls++
            return if (response.priorResponse == null) response.request.newBuilder().header("X-Retried", "1").build() else null
        }
    }

    private lateinit var server: MockWebServer
    private val settings = HttpLogSettingsRepository(NoOpStore, CoroutineScope(Dispatchers.Unconfined))

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(interceptors: Set<ApiInterceptor> = emptySet(), authenticators: Set<Authenticator> = emptySet()): OkHttpClient =
        NetworkModule.provideApiOkHttpClient(interceptors, authenticators, settings)

    private fun OkHttpClient.get(): Int =
        newCall(Request.Builder().url(server.url("/v1/items")).build()).execute().use { it.code }

    @Test
    fun `with no contributors the client sends the request untouched and returns a 401 as is`() {
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(401, client().get())
        assertNull(server.takeRequest().getHeader("Authorization"))
        assertEquals("no authenticator means no retry", 1, server.requestCount)
    }

    @Test
    fun `contributed interceptors run in ascending order regardless of set order`() {
        server.enqueue(MockResponse().setResponseCode(200))

        client(linkedSetOf(TracingInterceptor(200, "observer"), TracingInterceptor(100, "credentials"))).get()

        assertEquals("credentials,observer", server.takeRequest().getHeader("X-Trace"))
    }

    @Test
    fun `a contributed authenticator handles the 401`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200))
        val authenticator = RecordingAuthenticator()

        assertEquals(200, client(authenticators = setOf(authenticator)).get())
        assertEquals(1, authenticator.calls)
        server.takeRequest()
        assertEquals("1", server.takeRequest().getHeader("X-Retried"))
    }

    @Test(expected = IllegalStateException::class)
    fun `two authenticators are a wiring error that fails at startup`() {
        client(authenticators = setOf(RecordingAuthenticator(), RecordingAuthenticator()))
    }
}
