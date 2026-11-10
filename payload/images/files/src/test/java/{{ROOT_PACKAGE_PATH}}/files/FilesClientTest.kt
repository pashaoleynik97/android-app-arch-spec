package {{ROOT_PACKAGE}}.files

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import {{ROOT_PACKAGE}}.files.di.FilesModule
import {{ROOT_PACKAGE}}.network.log.HttpLogPolicy
import {{ROOT_PACKAGE}}.network.log.HttpLogSettingsRepository
import {{ROOT_PACKAGE}}.network.log.HttpLogSettingsStore

/** The production client (built by the real `@Provides` function) against a local server. */
class FilesClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(vararg authorizers: FilesAuthorizer): OkHttpClient =
        FilesModule.provideFilesOkHttpClient(authorizers.toSet(), httpLogSettings())

    private fun fetch(client: OkHttpClient) {
        server.enqueue(MockResponse().setResponseCode(200))
        client.newCall(Request.Builder().url(server.url("/v1/files/a.jpg")).build()).execute().close()
    }

    @Test
    fun `without an authorizer the request goes out unmodified`() {
        fetch(client())

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader("Cookie"))
    }

    @Test
    fun `a bearer authorizer attaches the current token, read per request`() {
        var token: String? = "t-1"
        val client = client(FilesAuthorizer.bearer { token })

        fetch(client)
        token = "t-2"
        fetch(client)

        assertEquals("Bearer t-1", server.takeRequest().getHeader("Authorization"))
        assertEquals("a rotated token is picked up on the next fetch", "Bearer t-2", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `a cookie authorizer attaches the token as a cookie`() {
        fetch(client(FilesAuthorizer.cookie("access_token") { "abc" }))

        assertEquals("access_token=abc", server.takeRequest().getHeader("Cookie"))
    }

    @Test
    fun `no credentials are sent while there is no session or the token is blank`() {
        fetch(client(FilesAuthorizer.bearer { null }))
        fetch(client(FilesAuthorizer.cookie("access_token") { "  " }))

        assertNull(server.takeRequest().getHeader("Authorization"))
        assertNull(server.takeRequest().getHeader("Cookie"))
    }

    @Test(expected = IllegalStateException::class)
    fun `two authorizers are a wiring error that fails fast`() {
        client(FilesAuthorizer.bearer { "a" }, FilesAuthorizer.cookie("c") { "b" })
    }

    /** The real repository over an in-memory store; logging only matters in that the client builds with it. */
    private fun httpLogSettings() = HttpLogSettingsRepository(
        store = object : HttpLogSettingsStore {
            override suspend fun read() = HttpLogPolicy()
            override suspend fun write(policy: HttpLogPolicy) = Unit
        },
        scope = CoroutineScope(UnconfinedTestDispatcher()),
    )
}
