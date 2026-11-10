package {{ROOT_PACKAGE}}.connectivity.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import {{ROOT_PACKAGE}}.connectivity.BuildConfig
import {{ROOT_PACKAGE}}.connectivity.config.ConnectivityConfig
import {{ROOT_PACKAGE}}.coretesting.TestDispatcherProvider
import java.util.concurrent.TimeUnit

class OkHttpServerHealthProbeTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun probe(healthPath: String = "health") = OkHttpServerHealthProbe(
        OkHttpClient.Builder().connectTimeout(1, TimeUnit.SECONDS).readTimeout(1, TimeUnit.SECONDS).build(),
        ConnectivityConfig(server.url("/api/").toString(), healthPath),
        TestDispatcherProvider(Dispatchers.IO),
    )

    @Test
    fun `a 2xx is reachable, and the path is relative to the base url`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))

        assertTrue(probe().isServerReachable())
        assertEquals("/api/health", server.takeRequest().path)
    }

    @Test
    fun `a leading slash in the health path does not escape the base path`() = runBlocking {
        server.enqueue(MockResponse())

        probe("/v1/health").isServerReachable()

        assertEquals("/api/v1/health", server.takeRequest().path)
    }

    @Test
    fun `a 503 and a dropped connection are not reachable`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertFalse(probe().isServerReachable())
        assertFalse(probe().isServerReachable())
    }

    @Test
    fun `the rendered build config is a valid probe target`() {
        ConnectivityConfig(BuildConfig.BASE_URL, BuildConfig.HEALTH_PATH)
    }
}
