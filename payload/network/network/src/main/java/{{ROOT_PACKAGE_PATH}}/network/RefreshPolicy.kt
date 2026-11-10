package {{ROOT_PACKAGE}}.network

/**
 * Whether a request may **extend the session** when the server answers 401 — attached per call as a Retrofit
 * `@Tag` parameter (`suspend fun list(@Tag policy: RefreshPolicy): Response<…>`), read by whichever
 * authenticator a session module installs on the business client.
 *
 * Declared here, not in the session module, so feature APIs can state it without depending on session code and
 * so the business client keeps building when no session module is installed (the tag is then simply unused).
 *
 * Why it exists: a timer-driven poll that silently refreshes on 401 keeps an untouched device signed in forever,
 * defeating the server's idle timeout. Background work passes [NeverRefresh] and just loses that one request once
 * the token lapses; the user's next action carries [AllowRefresh] and the server decides whether the session
 * still lives (DATA-19). A request **without** the tag behaves as [AllowRefresh]. The tag survives the
 * authenticator's retry copy (`newBuilder()` keeps tags).
 */
enum class RefreshPolicy {

    /** User-driven work: a 401 refreshes once and retries. */
    AllowRefresh,

    /** Timer- or socket-driven work: a 401 fails the request — it never refreshes and never ends the session. */
    NeverRefresh,
}
