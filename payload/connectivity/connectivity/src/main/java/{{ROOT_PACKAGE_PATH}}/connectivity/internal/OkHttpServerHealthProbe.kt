package {{ROOT_PACKAGE}}.connectivity.internal

import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import {{ROOT_PACKAGE}}.connectivity.config.ConnectivityConfig
import {{ROOT_PACKAGE}}.connectivity.di.ConnectivityClient
import {{ROOT_PACKAGE}}.libcommon.coroutines.DispatcherProvider
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import java.io.IOException
import javax.inject.Inject

/**
 * `GET <baseUrl><healthPath>` on the module's own client — unauthenticated, short timeouts, so the answer does not
 * depend on a session and a dead server is detected in seconds. Any 2xx = reachable; any IO failure = not.
 */
internal class OkHttpServerHealthProbe @Inject constructor(
    @param:ConnectivityClient private val client: OkHttpClient,
    private val config: ConnectivityConfig,
    private val dispatchers: DispatcherProvider,
) : ServerHealthProbe {

    private val logTag = logTag()

    override suspend fun isServerReachable(): Boolean = withContext(dispatchers.io) {
        val request = Request.Builder().url(config.healthUrl).get().build()
        try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: IOException) {
            Reporter.debug("health probe failed: ${e.message}", where = logTag)
            false
        }
    }
}
