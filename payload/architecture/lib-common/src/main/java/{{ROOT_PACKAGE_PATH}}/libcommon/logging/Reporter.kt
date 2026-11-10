package {{ROOT_PACKAGE}}.libcommon.logging

import android.util.Log
import {{ROOT_PACKAGE}}.libcommon.BuildConfig
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The app's single logging and telemetry seam. Nothing else calls `android.util.Log`.
 *
 * - **Local output is debug-only.** Logcat lines are written only when `BuildConfig.DEBUG`, under one tag
 *   (`Reporter`) with the caller in the prefix: `[D][LoginViewModel] submit`.
 * - **External reporters receive everything, in every build type.** Crash/telemetry back-ends plug in via
 *   [addExternalReporter]. Because release logs leave the device through them, anything secret (tokens,
 *   credentials, personal data) MUST be masked before it reaches a `Reporter` call.
 * - Every call names its origin: `Reporter.debug("loaded", where = logTag)` (see [logTag]).
 *
 * A global object rather than an injected service on purpose: logging must be reachable from anywhere,
 * including code that is not DI-constructed. Tests observe it by installing a recording
 * [ExternalReporter] and removing it afterwards.
 */
object Reporter {

    private const val TAG = "Reporter"

    /** A sink for log lines and exceptions — e.g. a crash-reporting SDK. */
    interface ExternalReporter {
        fun logReport(message: String)
        fun recordException(exception: Throwable)
    }

    /** Thread-safe: reporters may be added while another thread is logging. */
    private val externalReporters = CopyOnWriteArraySet<ExternalReporter>()

    private val localLogsEnabled: Boolean get() = BuildConfig.DEBUG

    fun addExternalReporter(reporter: ExternalReporter) {
        externalReporters.add(reporter)
    }

    fun removeExternalReporter(reporter: ExternalReporter) {
        externalReporters.remove(reporter)
    }

    fun removeAllExternalReporters() {
        externalReporters.clear()
    }

    fun debug(message: String, where: String) {
        val line = "[D][$where] $message"
        if (localLogsEnabled) Log.d(TAG, line)
        externalReporters.forEach { it.logReport(line) }
    }

    fun info(message: String, where: String) {
        val line = "[I][$where] $message"
        if (localLogsEnabled) Log.i(TAG, line)
        externalReporters.forEach { it.logReport(line) }
    }

    /** Something unexpected but survivable. Recorded as a non-fatal by external reporters. */
    fun warn(message: String, exception: Throwable? = null, where: String) {
        val line = "[W][$where] $message"
        if (localLogsEnabled) Log.w(TAG, line, exception)
        externalReporters.forEach { it.recordException(ReportedException("Warning ($line)", exception)) }
    }

    /** A failure. Recorded as a non-fatal by external reporters. */
    fun error(message: String, exception: Throwable? = null, where: String) {
        val line = "[E][$where] $message"
        if (localLogsEnabled) Log.e(TAG, line, exception)
        externalReporters.forEach { it.recordException(ReportedException("Error ($line)", exception)) }
    }
}

/** Wraps a reported problem so external reporters always get a throwable that carries the log line. */
class ReportedException(message: String, cause: Throwable?) : Exception(message, cause)
