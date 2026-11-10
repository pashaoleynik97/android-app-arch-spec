package {{ROOT_PACKAGE}}.auth.network

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import retrofit2.Response
import {{ROOT_PACKAGE}}.auth.SessionEndReason
import {{ROOT_PACKAGE}}.network.parseApiError

/** The parsed error envelope of an auth call: the typed [code] plus, on a refused refresh, the precise reason. */
internal data class ParsedAuthError(val code: AuthErrorCode, val sessionEndReason: SessionEndReason?)

/**
 * Parses the error envelope **once** (single-read body, DATA-14) through the network module's helper. `null` when
 * the body is absent or not the envelope — the caller falls back to a per-endpoint default code.
 */
internal fun parseAuthError(response: Response<*>, json: Json): ParsedAuthError? =
    parseApiError(response, json)?.let { details ->
        ParsedAuthError(AuthErrorCode.fromWire(details.error), details.details?.sessionEndReason())
    }

/** `details.reason` of an object-shaped `details`; any other shape (a list of field errors) carries none. */
private fun JsonElement.sessionEndReason(): SessionEndReason? {
    val reason = (this as? JsonObject)?.get("reason") as? JsonPrimitive ?: return null
    return if (reason.isString) SessionEndReason.fromWire(reason.content) else null
}
