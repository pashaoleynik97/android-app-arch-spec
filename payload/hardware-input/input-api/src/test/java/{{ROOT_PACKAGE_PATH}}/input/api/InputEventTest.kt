package {{ROOT_PACKAGE}}.input.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class InputEventTest {

    private val source = InputDriverId("debug")

    private fun event(
        data: String = "4006381333931",
        bytes: ByteArray? = data.toByteArray(),
        kind: String? = "ean13",
        extras: Map<String, String> = emptyMap(),
    ) = InputEvent(data = data, rawBytes = bytes, kind = kind, source = source, timestamp = 42L, extras = extras)

    @Test
    fun `two events with the same reading are equal even though their byte arrays are different objects`() {
        val first = event()
        val second = event()

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `raw bytes do not take part in equality at all`() {
        assertEquals(event(bytes = null), event(bytes = byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `a different payload, kind or extras makes a different event`() {
        assertNotEquals(event(), event(data = "other"))
        assertNotEquals(event(), event(kind = null))
        assertNotEquals(event(), event(extras = mapOf("codeId" to "d")))
    }

    @Test
    fun `toString shows the byte count, not the bytes`() {
        assertEquals(
            "InputEvent(data='ab', kind=qr, source=debug, timestamp=42, bytes=2, extras={})",
            event(data = "ab", kind = "qr").toString(),
        )
    }
}
