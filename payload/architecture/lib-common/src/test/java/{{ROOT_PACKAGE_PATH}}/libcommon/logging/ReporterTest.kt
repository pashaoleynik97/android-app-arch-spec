package {{ROOT_PACKAGE}}.libcommon.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReporterTest {

    private class Recording : Reporter.ExternalReporter {
        val lines = mutableListOf<String>()
        val exceptions = mutableListOf<Throwable>()
        override fun logReport(message: String) {
            lines += message
        }
        override fun recordException(exception: Throwable) {
            exceptions += exception
        }
    }

    @After
    fun tearDown() = Reporter.removeAllExternalReporters()

    @Test
    fun `debug and info lines reach external reporters with level and origin`() {
        val recording = Recording().also(Reporter::addExternalReporter)

        Reporter.debug("loaded", where = "Screen")
        Reporter.info("ready", where = "Screen")

        assertEquals(listOf("[D][Screen] loaded", "[I][Screen] ready"), recording.lines)
    }

    @Test
    fun `errors and warnings are recorded as exceptions that keep their cause`() {
        val recording = Recording().also(Reporter::addExternalReporter)
        val cause = IllegalStateException("boom")

        Reporter.error("save failed", exception = cause, where = "Repo")
        Reporter.warn("odd payload", where = "Mapper")

        assertEquals(2, recording.exceptions.size)
        assertSame(cause, recording.exceptions[0].cause)
        assertTrue(recording.exceptions[0].message!!.contains("[E][Repo] save failed"))
        assertTrue(recording.exceptions[1].message!!.contains("[W][Mapper] odd payload"))
    }

    @Test
    fun `a removed reporter receives nothing more`() {
        val recording = Recording().also(Reporter::addExternalReporter)

        Reporter.removeExternalReporter(recording)
        Reporter.debug("after removal", where = "Test")

        assertTrue(recording.lines.isEmpty())
    }

    @Test
    fun `logTag is the simple class name`() {
        assertEquals("ReporterTest", logTag())
    }
}
