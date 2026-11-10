package {{ROOT_PACKAGE}}.auth.config

/**
 * Tuning of the activity-gated keep-alive. Not environment, so the module provides it with these defaults
 * (MOD-15 allows that); tests inject their own.
 *
 * The keep-alive **extends** a session that is in use; it never ends one. With no interaction since the current
 * token was issued it parks, the token lapses, and the next user action refreshes reactively — the server then
 * decides whether the session survived (ADR-0009).
 *
 * @param enabled master switch. Tests default it off so its refreshes never trip unrelated assertions.
 * @param leadMillis how long before the access token lapses to refresh; coerced to at most half the token's
 *   lifetime so very short lifetimes still behave.
 * @param retryDelayMillis backoff after a transient failure near expiry.
 */
data class ProactiveRefreshConfig(
    val enabled: Boolean = true,
    val leadMillis: Long = 60_000L,
    val retryDelayMillis: Long = 5_000L,
)
