package {{ROOT_PACKAGE}}.input.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebugInputEventsTest {

    @Test
    fun `the action follows the installed package name`() {
        assertEquals("a.b.debug.input.DEBUG_EVENT", DebugInputEvents.action("a.b.debug"))
    }

    @Test
    fun `a broadcast without data is ignored`() {
        assertNull(DebugInputEvents.toEvent(data = null, kind = "qr", nowMillis = 1))
    }

    @Test
    fun `a broadcast becomes an event from the debug driver with a normalized kind`() {
        val event = DebugInputEvents.toEvent(data = "4006381333931", kind = " EAN13 ", nowMillis = 7)!!

        assertEquals("4006381333931", event.data)
        assertEquals("ean13", event.kind)
        assertEquals(DebugBroadcastInputProvider.ID, event.source)
        assertEquals(7, event.timestamp)
        assertEquals("the raw kind is kept for diagnostics", " EAN13 ", event.extras[DebugInputEvents.EXTRA_KIND])
        assertEquals("adb", event.extras["injectedBy"])
    }

    @Test
    fun `a missing or blank kind is null`() {
        assertNull(DebugInputEvents.toEvent(data = "x", kind = null, nowMillis = 0)!!.kind)
        assertNull(DebugInputEvents.toEvent(data = "x", kind = "  ", nowMillis = 0)!!.kind)
    }
}
