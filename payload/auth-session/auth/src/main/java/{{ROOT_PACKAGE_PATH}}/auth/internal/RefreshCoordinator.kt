package {{ROOT_PACKAGE}}.auth.internal

/**
 * The single refresh entrypoint, as the OkHttp authenticator sees it. Implemented by the session manager and
 * kept internal so nothing outside the module can drive a refresh around the single-flight slot.
 */
internal interface RefreshCoordinator {

    /**
     * Single-flight refresh. [seenAccessToken] is the token the caller observed (the bearer of the request that
     * got 401); if the session already rotated past it, the current token is returned with no network call.
     * `null` on cold start.
     */
    suspend fun refresh(seenAccessToken: String?): RefreshOutcome

    /**
     * The retried request was refused with a fresh token: end the session — but only if it is still the one the
     * caller operated on ([seenAccessToken]); a session that changed meanwhile is left alone (zombie guard).
     */
    suspend fun expireSession(seenAccessToken: String?)
}
