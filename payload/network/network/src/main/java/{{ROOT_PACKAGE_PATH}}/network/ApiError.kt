package {{ROOT_PACKAGE}}.network

import kotlinx.serialization.json.Json
import retrofit2.Response
import {{ROOT_PACKAGE}}.network.dto.ApiErrorDetailsDto
import {{ROOT_PACKAGE}}.network.dto.ApiErrorResponseDto
import java.io.IOException

/**
 * Reads the error envelope out of a non-2xx Retrofit [response], or `null` when the body is absent or is not
 * the envelope (an empty 401, an HTML 502 from a proxy). Never throws.
 *
 * `errorBody()` is a **single-read stream**: call this (or [parseApiErrorCode]) **once** per response and keep
 * the result in a local — a second read silently yields nothing and misclassifies the response (DATA-14).
 * Most callers want only the code; this variant exists for errors whose `details` are part of the contract.
 */
fun parseApiError(response: Response<*>, json: Json): ApiErrorDetailsDto? =
    try {
        response.errorBody()?.string()?.takeIf { it.isNotBlank() }?.let { body ->
            json.decodeFromString<ApiErrorResponseDto>(body).error
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        // SerializationException is an IllegalArgumentException: a body that is not the envelope.
        null
    }

/** The raw wire code of the error, or `null`. Same single-read rule as [parseApiError]. */
fun parseApiErrorCode(response: Response<*>, json: Json): String? = parseApiError(response, json)?.error

/**
 * The error code of an OkHttp [response] **without consuming its body** — for interceptors and authenticators,
 * which run before the service that will classify the same response (DATA-14). Reads at most [limitBytes]
 * (error envelopes are tiny; the cap keeps a misbehaving endpoint from buffering a large body). Never throws.
 */
fun peekApiErrorCode(response: okhttp3.Response, json: Json, limitBytes: Long = PEEK_LIMIT_BYTES): String? =
    try {
        response.peekBody(limitBytes).string()
            .takeIf { it.isNotBlank() }
            ?.let { json.decodeFromString<ApiErrorResponseDto>(it).error.error }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

/** Default cap for [peekApiErrorCode]. */
const val PEEK_LIMIT_BYTES: Long = 4_096L
