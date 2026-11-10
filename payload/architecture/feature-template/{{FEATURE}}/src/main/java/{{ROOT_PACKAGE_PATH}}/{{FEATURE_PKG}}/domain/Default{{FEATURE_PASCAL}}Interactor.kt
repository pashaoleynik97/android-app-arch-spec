package {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.domain

import kotlinx.coroutines.withContext
import {{ROOT_PACKAGE}}.libcommon.coroutines.DispatcherProvider
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import javax.inject.Inject

/**
 * Production [{{FEATURE_PASCAL}}Interactor]. Scaffolded with a local, empty summary so the feature runs end to
 * end before it has a data source; replace the body with the real read (a repository or service injected
 * through the constructor) and map its failures onto [{{FEATURE_PASCAL}}Result].
 *
 * Main-safe: it moves its own work to [DispatcherProvider.io], so callers never pick a dispatcher. Let
 * `CancellationException` propagate — catch only the failures you can classify.
 */
class Default{{FEATURE_PASCAL}}Interactor @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : {{FEATURE_PASCAL}}Interactor {

    private val logTag = logTag()

    override suspend fun loadSummary(): {{FEATURE_PASCAL}}Result = withContext(dispatchers.io) {
        Reporter.debug("loadSummary", where = logTag)
        {{FEATURE_PASCAL}}Result.Success({{FEATURE_PASCAL}}Summary(itemCount = 0))
    }
}
