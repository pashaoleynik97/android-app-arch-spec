package {{ROOT_PACKAGE}}.network.log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import {{ROOT_PACKAGE}}.network.BuildConfig
import {{ROOT_PACKAGE}}.network.di.NetworkScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The live HTTP logging policy: read by every logging interceptor **on each message**, written by whatever
 * diagnostics UI the app has (a debug menu).
 *
 * [policy] starts at the defaults rather than at `null` (the exception KERNEL-18 allows for a flow that only
 * renders): the defaults (off, 512 KB) are exactly what a never-configured device should do, so the moment before
 * the disk read lands needs no special state.
 */
@Singleton
class HttpLogSettingsRepository @Inject constructor(
    private val store: HttpLogSettingsStore,
    @NetworkScope private val scope: CoroutineScope,
) {

    private val logTag = logTag()

    private val mPolicy = MutableStateFlow(HttpLogPolicy())
    val policy: StateFlow<HttpLogPolicy> = mPolicy.asStateFlow()

    /**
     * Whether full HTTP logging can be turned on at all. Release builds install no logging interceptor (and the
     * Reporter prints nothing locally), so a diagnostics UI should show the switch disabled there.
     */
    val isAvailable: Boolean = BuildConfig.DEBUG

    init {
        scope.launch {
            val loaded = store.read()
            mPolicy.value = loaded
            Reporter.debug("HTTP log policy loaded: $loaded", where = logTag)
        }
    }

    /** Applies at once (the next logged message obeys it) and persists. */
    suspend fun setPrintFullBodies(enabled: Boolean) = persist { it.copy(printFullBodies = enabled) }

    /** Applies at once and persists. */
    suspend fun setLimit(limit: HttpLogLimit) = persist { it.copy(limit = limit) }

    private suspend fun persist(transform: (HttpLogPolicy) -> HttpLogPolicy) {
        mPolicy.update(transform)
        val updated = mPolicy.value
        store.write(updated)
        Reporter.debug("HTTP log policy set: $updated", where = logTag)
    }
}
