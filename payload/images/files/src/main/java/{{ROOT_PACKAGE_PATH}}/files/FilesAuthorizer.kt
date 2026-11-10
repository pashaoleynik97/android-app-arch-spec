package {{ROOT_PACKAGE}}.files

import okhttp3.Request

/**
 * How a files request proves who is asking — the pluggable auth strategy of the `@FilesClient`.
 *
 * - **No authorizer bound** (no session module installed): requests go out unmodified. Public files work; protected
 *   ones fail to load and the UI shows its placeholder — never a crash.
 * - With the `auth-session` component installed, `:files` contributes [bearer] over the session's current access
 *   token automatically (see the module's `src/withAuth`).
 * - A backend that wants the token as a cookie (common for file hosts that serve `<img>` tags to browsers too)
 *   binds [cookie] instead — in `:app`, replacing the automatic one by removing that source set.
 *
 * Contribute with `@Provides @IntoSet` (the client accepts **at most one**). The token is read **per request**, so a
 * rotation is picked up on the next fetch; reads must be cheap and non-blocking (an in-memory value).
 */
fun interface FilesAuthorizer {

    /** Returns [request] with credentials attached, or [request] itself when there are none. */
    fun authorize(request: Request): Request

    companion object {

        /** `Authorization: Bearer <token>`; unmodified while [token] is null or blank. */
        fun bearer(token: () -> String?): FilesAuthorizer = FilesAuthorizer { request ->
            val value = token()?.takeIf { it.isNotBlank() } ?: return@FilesAuthorizer request
            request.newBuilder().header("Authorization", "Bearer $value").build()
        }

        /** `Cookie: <name>=<token>`; unmodified while [token] is null or blank. */
        fun cookie(name: String, token: () -> String?): FilesAuthorizer = FilesAuthorizer { request ->
            val value = token()?.takeIf { it.isNotBlank() } ?: return@FilesAuthorizer request
            request.newBuilder().header("Cookie", "$name=$value").build()
        }
    }
}
