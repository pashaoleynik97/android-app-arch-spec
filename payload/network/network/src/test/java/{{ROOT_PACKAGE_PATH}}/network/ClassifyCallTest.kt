package {{ROOT_PACKAGE}}.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Tag
import {{ROOT_PACKAGE}}.network.dto.ApiResponse

/**
 * The service shape every feature copies (DATA-10 … DATA-16), exercised end to end: a Retrofit API over the
 * example envelope, classified by [classifyCall] into a per-endpoint sealed result. The JSON bodies are the
 * ones the mitm-testing fake backend serves for `GET v1/items/{id}`, so this also pins the example contract.
 */
class ClassifyCallTest {

    @Serializable
    data class ItemDto(val id: String, val title: String, val status: String, val note: String? = null)

    interface ItemsApi {
        @GET("v1/items/{id}")
        suspend fun item(@Path("id") id: String, @Tag policy: RefreshPolicy): Response<ApiResponse<ItemDto>>
    }

    sealed interface ItemResult {
        data class Success(val title: String) : ItemResult
        data object SessionExpired : ItemResult
        data class Refused(val code: String?) : ItemResult
        data object Unavailable : ItemResult
        data class Transient(val cause: Throwable?) : ItemResult
    }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private lateinit var server: MockWebServer
    private lateinit var api: ItemsApi

    /** The example ladder, as a feature's `RetrofitXService` would write it. */
    private suspend fun get(id: String): ItemResult = classifyCall(
        dispatcher = Dispatchers.IO,
        call = { api.item(id, RefreshPolicy.AllowRefresh) },
        onFailure = { ItemResult.Transient(it) },
    ) { r ->
        when {
            r.isSuccessful -> r.body()?.data?.let { ItemResult.Success(it.title) } ?: ItemResult.Transient(null)
            r.code() == HttpStatus.UNAUTHORIZED -> ItemResult.SessionExpired
            r.code() == HttpStatus.FORBIDDEN -> ItemResult.Refused(parseApiErrorCode(r, json))
            r.code() == HttpStatus.NOT_FOUND -> ItemResult.Unavailable
            else -> ItemResult.Transient(null)
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a 2xx envelope is unwrapped, unknown fields ignored, and the request carries the path`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"id":"item-1","title":"Alpha example 1","status":"open","updatedAt":"t","completedAt":null,"note":null},"timestamp":"t"}""",
            ),
        )

        assertEquals(ItemResult.Success("Alpha example 1"), get("item-1"))
        assertEquals("/v1/items/item-1", server.takeRequest().path)
    }

    @Test
    fun `a 2xx without a body is transient, never a success with defaults`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        assertEquals(ItemResult.Transient(null), get("item-1"))
    }

    @Test
    fun `a 2xx whose body does not decode is transient`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"unexpected":true}}"""))

        assertTrue(get("item-1") is ItemResult.Transient)
    }

    @Test
    fun `statuses map to their rungs and the 403 carries its wire code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"error":"ITEM_NOT_ALLOWED","message":"x","details":[]},"timestamp":"t"}"""))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"error":"ITEM_NOT_FOUND","message":"x","details":[]},"timestamp":"t"}"""))
        server.enqueue(MockResponse().setResponseCode(500))

        assertEquals(ItemResult.SessionExpired, get("a"))
        assertEquals(ItemResult.Refused("ITEM_NOT_ALLOWED"), get("a"))
        assertEquals(ItemResult.Unavailable, get("a"))
        assertEquals(ItemResult.Transient(null), get("a"))
    }

    @Test
    fun `a dropped connection is transient with its cause`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = get("item-1")

        assertTrue(result is ItemResult.Transient && result.cause is java.io.IOException)
    }

    @Test
    fun `cancellation propagates instead of becoming a failure`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}").setHeadersDelay(5, java.util.concurrent.TimeUnit.SECONDS))
        val started = CompletableDeferred<Unit>()
        val call = async(Dispatchers.IO) {
            classifyCall(
                dispatcher = Dispatchers.IO,
                call = { started.complete(Unit); api.item("slow", RefreshPolicy.NeverRefresh) },
                onFailure = { ItemResult.Transient(it) },
            ) { ItemResult.Success("never") }
        }
        withTimeout(2_000) { started.await() }
        call.cancel()

        val outcome = runCatching { call.await() }
        assertTrue("a cancelled call must not return a result", outcome.exceptionOrNull() is CancellationException)
    }
}
