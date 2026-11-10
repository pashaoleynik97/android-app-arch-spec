package {{ROOT_PACKAGE}}.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * EXAMPLE CONTRACT — adapt to your backend (see specs/recommended/network.md, "Adapting this to your app").
 *
 *   success: { "data": <T>, "timestamp": "…" }
 *   error:   { "error": { "error": "<CODE>", "message": "…", "details": <any> }, "timestamp": "…" }
 *
 * The same shapes are served by the mitm-testing fake backend (`.mitm_scripts/_harness/fake_backend.py`),
 * so a change here goes together with a change there.
 */

/** Success envelope. One generic type for every endpoint — features declare only their `data` payload DTO. */
@Serializable
data class ApiResponse<T>(
    val data: T,
    val timestamp: String? = null,
)

/** Error envelope (the body of a non-2xx response). */
@Serializable
data class ApiErrorResponseDto(
    val error: ApiErrorDetailsDto,
    val timestamp: String? = null,
)

@Serializable
data class ApiErrorDetailsDto(
    /** The machine-readable error code exactly as on the wire. Features map it onto their own vocabulary (DATA-24). */
    val error: String,
    val message: String? = null,
    /** Free-form payload (a list of field errors, an object with a reason, …). Captured, interpreted only by the feature that knows its shape. */
    val details: JsonElement? = null,
)

/** Generic `{ "success": true }` payload of fire-and-forget writes (e.g. a logout). */
@Serializable
data class SuccessDto(
    val success: Boolean,
    val message: String? = null,
)
