package {{ROOT_PACKAGE}}.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class ApiErrorTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun error(code: Int, body: String): Response<Unit> =
        Response.error(code, body.toResponseBody("application/json".toMediaType()))

    @Test
    fun `the envelope's code, message and details are read`() {
        val r = error(409, """{"error":{"error":"ITEM_STATUS_CHANGED","message":"no longer open","details":["a"]},"timestamp":"t"}""")

        val details = parseApiError(r, json)!!

        assertEquals("ITEM_STATUS_CHANGED", details.error)
        assertEquals("no longer open", details.message)
        assertEquals("[\"a\"]", details.details.toString())
    }

    @Test
    fun `an empty body, an html page and a foreign json all read as no code`() {
        assertNull(parseApiErrorCode(error(401, ""), json))
        assertNull(parseApiErrorCode(error(502, "<html>bad gateway</html>"), json))
        assertNull(parseApiErrorCode(error(400, """{"message":"not the envelope"}"""), json))
    }

    @Test
    fun `the error body is single-read - a second parse yields nothing`() {
        val r = error(403, """{"error":{"error":"FORBIDDEN"}}""")

        assertEquals("FORBIDDEN", parseApiErrorCode(r, json))
        assertNull("parse once and keep the result (DATA-14)", parseApiErrorCode(r, json))
    }

    @Test
    fun `peeking the code leaves the body readable for the caller`() {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"error":"FORBIDDEN","message":"x"}}"""))
            val response = OkHttpClient().newCall(Request.Builder().url(server.url("/v1/items/1")).build()).execute()

            assertEquals("FORBIDDEN", peekApiErrorCode(response, json))
            assertEquals("""{"error":{"error":"FORBIDDEN","message":"x"}}""", response.body!!.string())
        } finally {
            server.shutdown()
        }
    }
}
