package {{ROOT_PACKAGE}}.network.access

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import {{ROOT_PACKAGE}}.network.ApiInterceptor

class AccessLossInterceptorTest {

    /** Records reports; registering and the fallback are not exercised here. */
    private class RecordingAccessLossBus : AccessLossBus {
        val reported = mutableListOf<AccessLoss>()
        override fun report(loss: AccessLoss) {
            reported += loss
        }
        override fun register(handler: AccessLossHandler) = AccessLossBus.Registration {}
        override fun setFallback(handler: AccessLossHandler) = Unit
    }

    private lateinit var server: MockWebServer
    private val bus = RecordingAccessLossBus()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val interceptor = AccessLossInterceptor(bus, json, AccessLossConfig())
    private val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun call(path: String = "/v1/items/1") = client.newCall(Request.Builder().url(server.url(path)).build()).execute()

    private fun forbidden(code: String) =
        MockResponse().setResponseCode(403).setBody("""{"error":{"error":"$code","message":"m","details":[]},"timestamp":"t"}""")

    @Test
    fun `a scope-level code is a scope loss`() {
        server.enqueue(forbidden("SCOPE_ACCESS_REVOKED"))

        call().close()

        assertEquals(listOf(AccessLoss.ScopeLost("/v1/items/1", "SCOPE_ACCESS_REVOKED")), bus.reported)
    }

    @Test
    fun `any other 403 is a forbidden with its code, an unparseable one with none`() {
        server.enqueue(forbidden("ITEM_NOT_ALLOWED"))
        server.enqueue(MockResponse().setResponseCode(403).setBody("<html>no</html>"))

        call().close()
        call().close()

        assertEquals(
            listOf(AccessLoss.Forbidden("/v1/items/1", "ITEM_NOT_ALLOWED"), AccessLoss.Forbidden("/v1/items/1", null)),
            bus.reported,
        )
    }

    @Test
    fun `the body is still readable by the caller afterwards`() {
        server.enqueue(forbidden("ITEM_NOT_ALLOWED"))

        val body = call().use { it.body!!.string() }

        assertTrue("peekBody must not consume the single-read stream", body.contains("ITEM_NOT_ALLOWED"))
    }

    @Test
    fun `non-403 responses report nothing`() {
        listOf(200, 401, 404, 500).forEach { server.enqueue(MockResponse().setResponseCode(it)) }

        repeat(4) { call().close() }

        assertTrue(bus.reported.isEmpty())
    }

    @Test
    fun `it observes in the observer band, after credentials`() {
        assertEquals(ApiInterceptor.ORDER_OBSERVERS, interceptor.order)
    }
}
