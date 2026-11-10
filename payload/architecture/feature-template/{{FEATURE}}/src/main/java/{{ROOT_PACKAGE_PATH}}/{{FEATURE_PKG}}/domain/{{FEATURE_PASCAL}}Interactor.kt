package {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.domain

/** What the {{FEATURE}} screen shows. Plain domain data: no Android or UI types. */
data class {{FEATURE_PASCAL}}Summary(val itemCount: Int)

/**
 * Feature-level outcome of loading the summary. A sealed result, never an exception or an HTTP code: the
 * ViewModel branches on outcomes only, and every outcome is a case the compiler makes it handle.
 */
sealed interface {{FEATURE_PASCAL}}Result {
    data class Success(val summary: {{FEATURE_PASCAL}}Summary) : {{FEATURE_PASCAL}}Result

    /** Could not load right now (offline, server trouble); retrying may help. */
    data object Unavailable : {{FEATURE_PASCAL}}Result
}

/**
 * The only thing the {{FEATURE}} ViewModels talk to. An interface so UI tests use a hand-written fake;
 * [Default{{FEATURE_PASCAL}}Interactor] is the production binding.
 */
interface {{FEATURE_PASCAL}}Interactor {
    suspend fun loadSummary(): {{FEATURE_PASCAL}}Result
}
