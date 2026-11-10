package {{ROOT_PACKAGE}}.auth.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import {{ROOT_PACKAGE}}.auth.support.FakeAuthManager
import {{ROOT_PACKAGE}}.network.ApiInterceptor

class AuthInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun call(token: String?) {
        server.enqueue(MockResponse())
        OkHttpClient.Builder().addInterceptor(AuthInterceptor(FakeAuthManager(token))).build()
            .newCall(Request.Builder().url(server.url("/v1/items")).build()).execute().close()
    }

    @Test
    fun `attaches the bearer when a session exists`() {
        call("fake-access-1")

        assertEquals("Bearer fake-access-1", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `sends no authorization without a session`() {
        call(null)

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `it sits in the credentials band of the business client`() {
        assertEquals(ApiInterceptor.ORDER_CREDENTIALS, AuthInterceptor(FakeAuthManager()).order)
    }
}
