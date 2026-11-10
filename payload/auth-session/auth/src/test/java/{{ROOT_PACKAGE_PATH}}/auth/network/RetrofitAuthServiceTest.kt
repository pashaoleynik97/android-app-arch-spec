package {{ROOT_PACKAGE}}.auth.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
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
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import {{ROOT_PACKAGE}}.auth.SessionEndReason
import {{ROOT_PACKAGE}}.auth.SessionUser
import {{ROOT_PACKAGE}}.coretesting.TestDispatcherProvider

/**
 * The real classifier over real Retrofit + the real converter (TEST-20). Bodies are exactly what the
 * mitm-testing fake backend answers for the example contract, so a drift between the two fails here.
 */
class RetrofitAuthServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: RetrofitAuthService

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<AuthApi>()
        service = RetrofitAuthService(api, TestDispatcherProvider(Dispatchers.IO), json)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun error(status: Int, code: String, details: String = "[]") = MockResponse().setResponseCode(status)
        .setBody("""{"error":{"error":"$code","message":"m","details":$details},"timestamp":"2026-01-01T00:00:00.000Z"}""")

    // region login

    @Test
    fun `a login 2xx is a success with the user, and posts the example body`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"accessToken":"fake-access-1","refreshToken":"fake-refresh-1","expiresIn":3600,
                "user":{"id":"user-1","email":"user@example.test","displayName":"Example User"}},"timestamp":"t"}""",
            ),
        )

        val result = service.login("user@example.test", "password")

        assertEquals(
            AuthNetworkResult.Success("fake-access-1", "fake-refresh-1", 3600, SessionUser("user-1", "user@example.test", "Example User")),
            result,
        )
        val request = server.takeRequest()
        assertEquals("/v1/auth/login", request.path)
        assertEquals("""{"email":"user@example.test","password":"password"}""", request.body.readUtf8())
    }

    @Test
    fun `a login 401 is a definitive refusal, with or without the envelope`() = runBlocking {
        server.enqueue(error(401, "INVALID_CREDENTIALS"))
        server.enqueue(MockResponse().setResponseCode(401))

        repeat(2) { assertEquals(AuthNetworkResult.AuthRejected(AuthErrorCode.INVALID_CREDENTIALS), service.login("a@b", "x")) }
    }

    @Test
    fun `a 400 or 403 is definitive only for the known login codes`() = runBlocking {
        server.enqueue(error(400, "VALIDATION_ERROR"))
        server.enqueue(error(403, "ACCOUNT_DISABLED"))
        server.enqueue(error(400, "SOMETHING_NEW"))

        assertEquals(AuthNetworkResult.AuthRejected(AuthErrorCode.VALIDATION_ERROR), service.login("a@b", "x"))
        assertEquals(AuthNetworkResult.AuthRejected(AuthErrorCode.ACCOUNT_DISABLED), service.login("a@b", "x"))
        assertEquals(AuthNetworkResult.Transient(null), service.login("a@b", "x"))
    }

    // endregion

    // region refresh

    @Test
    fun `a refresh 2xx is a success with no user`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"accessToken":"fake-access-2","refreshToken":"fake-refresh-2","expiresIn":60},"timestamp":"t"}"""))

        assertEquals(AuthNetworkResult.Success("fake-access-2", "fake-refresh-2", 60, user = null), service.refresh("fake-refresh-1"))
        assertEquals("""{"refreshToken":"fake-refresh-1"}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `a refresh 401 carries its code and the precise reason from details`() = runBlocking {
        server.enqueue(error(401, "REFRESH_TOKEN_INVALID"))
        server.enqueue(error(401, "SESSION_REVOKED", """{"reason":"new_login"}"""))
        server.enqueue(error(401, "SESSION_REVOKED", """{"reason":"something_future"}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("<html/>"))

        assertEquals(AuthNetworkResult.AuthRejected(AuthErrorCode.REFRESH_TOKEN_INVALID), service.refresh("r"))
        assertEquals(AuthNetworkResult.AuthRejected(AuthErrorCode.SESSION_REVOKED, SessionEndReason.NEW_LOGIN), service.refresh("r"))
        assertEquals("an unknown reason is null, never a crash", AuthNetworkResult.AuthRejected(AuthErrorCode.SESSION_REVOKED, null), service.refresh("r"))
        assertEquals("no envelope: the endpoint's default code", AuthNetworkResult.AuthRejected(AuthErrorCode.REFRESH_TOKEN_INVALID), service.refresh("r"))
    }

    @Test
    fun `5xx, 429, blank tokens, malformed bodies and dropped connections are transient`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setBody("""{"data":{"accessToken":"","refreshToken":"r","expiresIn":60}}"""))
        server.enqueue(MockResponse().setBody("""{"data":{"unexpected":1}}"""))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        repeat(5) { assertTrue(service.refresh("r") is AuthNetworkResult.Transient) }
    }

    @Test
    fun `a 401 on refresh triggers no second request - this client has no authenticator`() = runBlocking {
        server.enqueue(error(401, "REFRESH_TOKEN_INVALID"))

        service.refresh("r")

        assertEquals(1, server.requestCount)
    }

    // endregion

    // region logout

    @Test
    fun `logout carries the bearer, and any answer below 500 counts as done`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"success":true},"timestamp":"t"}"""))
        server.enqueue(error(401, "TOKEN_INVALID"))
        server.enqueue(MockResponse().setResponseCode(503))

        assertTrue(service.logout("fake-access-1"))
        assertEquals("Bearer fake-access-1", server.takeRequest().getHeader("Authorization"))
        assertTrue("idempotent: an already-dead token is done", service.logout("x"))
        assertFalse("5xx: the caller may retry", service.logout("x"))
    }

    // endregion
}
