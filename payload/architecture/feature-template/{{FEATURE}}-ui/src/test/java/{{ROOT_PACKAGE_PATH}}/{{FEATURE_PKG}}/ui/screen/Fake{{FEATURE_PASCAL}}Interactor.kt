package {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.ui.screen

import kotlinx.coroutines.CompletableDeferred
import {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.domain.{{FEATURE_PASCAL}}Interactor
import {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.domain.{{FEATURE_PASCAL}}Result
import {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.domain.{{FEATURE_PASCAL}}Summary

/**
 * Hand-written fake of the domain interactor. Answers with [result] immediately, or — when [gate] is set —
 * suspends until the test completes the gate, so in-flight states are observable deterministically.
 */
class Fake{{FEATURE_PASCAL}}Interactor(
    var result: {{FEATURE_PASCAL}}Result = {{FEATURE_PASCAL}}Result.Success({{FEATURE_PASCAL}}Summary(itemCount = 3)),
) : {{FEATURE_PASCAL}}Interactor {

    var gate: CompletableDeferred<{{FEATURE_PASCAL}}Result>? = null
    var calls = 0
        private set

    override suspend fun loadSummary(): {{FEATURE_PASCAL}}Result {
        calls++
        return gate?.await() ?: result
    }
}
