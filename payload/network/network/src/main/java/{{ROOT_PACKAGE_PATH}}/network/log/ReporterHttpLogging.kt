package {{ROOT_PACKAGE}}.network.log

import okhttp3.logging.HttpLoggingInterceptor
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter

/**
 * A body-level OkHttp logging interceptor whose output goes through the [Reporter] (tagged [where]), so HTTP
 * traffic obeys the app's single logging seam (KERNEL-01). Shared by every client in the app — the business
 * client, the session client, an image client — so they all mask and chunk the same way.
 *
 * Each message is masked ([HttpLogMasking]) **before** chunking — a split must not cut a secret in two and hide
 * it from the mask — then split by [HttpLogChunker] under the policy read **per message**, so a toggle applies
 * to the next request, not the next process. Parts of a split body are paced ([pauseForLogDaemon]).
 *
 * **Install only on debug builds** (`if (BuildConfig.DEBUG)` at the call site): at body level the interceptor
 * buffers request and response bodies, which release builds must neither pay for nor risk logging.
 */
fun reporterHttpLoggingInterceptor(where: String, settings: HttpLogSettingsRepository): HttpLoggingInterceptor =
    HttpLoggingInterceptor { message ->
        val entries = HttpLogChunker.chunk(HttpLogMasking.mask(message), settings.policy.value)
        entries.forEachIndexed { index, entry ->
            Reporter.debug(entry, where = where)
            if (index < entries.lastIndex) pauseForLogDaemon()
        }
    }.apply { level = HttpLoggingInterceptor.Level.BODY }

/**
 * Lets logd keep up between the parts of one split body: written back-to-back from one thread, a long run of
 * entries loses contiguous chunks even when the ring buffer has room — the daemon's intake is outrun, not the
 * buffer. A millisecond per part is enough; nothing sleeps for a body that fits one entry, and the path exists
 * only in debug builds.
 */
private fun pauseForLogDaemon() {
    try {
        Thread.sleep(LOG_PACING_MILLIS)
    } catch (interrupted: InterruptedException) {
        // A cancelled call must stay cancelled: restore the flag and stop pacing, never swallow it.
        Thread.currentThread().interrupt()
    }
}

private const val LOG_PACING_MILLIS = 1L
