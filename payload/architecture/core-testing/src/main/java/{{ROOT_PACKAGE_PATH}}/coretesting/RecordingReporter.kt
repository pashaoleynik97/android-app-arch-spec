package {{ROOT_PACKAGE}}.coretesting

import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Captures what code under test sends to [Reporter], for tests that assert something was (or was not)
 * reported. Install it as a rule; it detaches itself after each test so captures never leak between tests.
 *
 * ```
 * @get:Rule val reporter = RecordingReporter()
 * … assertTrue(reporter.lines.any { "dropped" in it })
 * ```
 */
class RecordingReporter : TestWatcher(), Reporter.ExternalReporter {

    private val _lines = mutableListOf<String>()
    private val _exceptions = mutableListOf<Throwable>()

    val lines: List<String> get() = synchronized(this) { _lines.toList() }
    val exceptions: List<Throwable> get() = synchronized(this) { _exceptions.toList() }

    override fun logReport(message: String) {
        synchronized(this) { _lines += message }
    }

    override fun recordException(exception: Throwable) {
        synchronized(this) { _exceptions += exception }
    }

    override fun starting(description: Description) = Reporter.addExternalReporter(this)

    override fun finished(description: Description) = Reporter.removeExternalReporter(this)
}
