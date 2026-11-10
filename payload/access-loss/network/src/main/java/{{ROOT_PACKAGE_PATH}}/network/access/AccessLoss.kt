package {{ROOT_PACKAGE}}.network.access

/**
 * A request the user's access could not serve — reported for **every** 403 on the business client, whoever made
 * the call (a screen, a background poll), and walked through the [AccessLossBus] chain.
 *
 * Two kinds, decided by the error code ([AccessLossConfig.scopeLevelCodes]):
 * - [ScopeLost] — the user lost access to the whole scope their session is bound to (an organisation, a tenant,
 *   a workspace): nothing in the app will work, so if no one else handles it the host recovers or ends the session.
 * - [Forbidden] — any other refusal: specific to what was being done, which only the asking feature can interpret.
 */
sealed interface AccessLoss {

    /** Request path that was refused (`/v1/items/42`) — for logs and for filters to match on. */
    val where: String

    /** The wire error code, when the body was the error envelope. */
    val code: String?

    data class Forbidden(override val where: String, override val code: String?) : AccessLoss

    data class ScopeLost(override val where: String, override val code: String?) : AccessLoss
}
