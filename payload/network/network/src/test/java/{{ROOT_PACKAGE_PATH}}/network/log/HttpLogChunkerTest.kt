package {{ROOT_PACKAGE}}.network.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The truncation contract, pinned without a device: short lines untouched, a cut line that says it was cut,
 * and a full body that can be pasted back together in order.
 */
class HttpLogChunkerTest {

    private fun bytes(s: String) = s.toByteArray(Charsets.UTF_8).size

    @Test
    fun `a message that fits is returned unchanged and unmarked`() {
        val message = "<-- 200 OK https://api.example.test/v1/items (12ms)"

        assertEquals(listOf(message), HttpLogChunker.chunk(message, HttpLogPolicy()))
        assertEquals(listOf(message), HttpLogChunker.chunk(message, HttpLogPolicy(printFullBodies = true)))
    }

    @Test
    fun `off - a long message becomes one entry that fits, ending in the hint`() {
        val message = "x".repeat(10_000)

        val entries = HttpLogChunker.chunk(message, HttpLogPolicy(printFullBodies = false))

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertTrue(bytes(entry) <= HttpLogChunker.ENTRY_BUDGET)
        assertTrue(entry, entry.contains("printFullBodies"))
        val head = entry.substringBefore(" … <<")
        assertTrue("the hint states how much was dropped", entry.contains("${message.length - head.length} more chars"))
    }

    @Test
    fun `on - a long message is split into numbered entries that reassemble to the original`() {
        val message = (1..2_000).joinToString(separator = ",") { "{\"id\":$it}" }

        val entries = HttpLogChunker.chunk(message, HttpLogPolicy(printFullBodies = true))

        assertTrue("expected more than one entry", entries.size > 1)
        entries.forEachIndexed { index, entry ->
            assertTrue(entry, entry.startsWith("[${index + 1}/${entries.size}] "))
            assertTrue(bytes(entry) <= HttpLogChunker.ENTRY_BUDGET)
        }
        assertEquals(message, entries.joinToString(separator = "") { it.substringAfter("] ") })
    }

    @Test
    fun `on - the byte budget is honoured for a body of two-byte characters`() {
        // U+00E9 encodes to two UTF-8 bytes: a character budget would let these entries past logd's byte limit.
        val message = "é".repeat(12_000)

        val entries = HttpLogChunker.chunk(message, HttpLogPolicy(printFullBodies = true))

        entries.forEach { assertTrue(bytes(it) <= HttpLogChunker.ENTRY_BUDGET) }
        assertEquals(message, entries.joinToString(separator = "") { it.substringAfter("] ") })
    }

    @Test
    fun `on - a surrogate pair is never split across entries`() {
        val message = "📦".repeat(4_000) // one emoji = one surrogate pair = four bytes

        val entries = HttpLogChunker.chunk(message, HttpLogPolicy(printFullBodies = true))

        entries.forEach { entry ->
            val body = entry.substringAfter("] ")
            assertTrue(!body.first().isLowSurrogate() && !body.last().isHighSurrogate())
        }
        assertEquals(message, entries.joinToString(separator = "") { it.substringAfter("] ") })
    }

    @Test
    fun `on - a body past the limit stops there and says so`() {
        val message = "y".repeat(HttpLogLimit.KB_512.bytes + 10_000)

        val entries = HttpLogChunker.chunk(message, HttpLogPolicy(printFullBodies = true, limit = HttpLogLimit.KB_512))

        assertTrue(entries.last(), entries.last().contains("truncated at 512 KB"))
        assertEquals(HttpLogLimit.KB_512.bytes, entries.dropLast(1).sumOf { it.substringAfter("] ").length })
    }

    @Test
    fun `on - a body inside the limit carries no limit marker`() {
        val entries = HttpLogChunker.chunk("z".repeat(20_000), HttpLogPolicy(printFullBodies = true))

        assertTrue(entries.last(), entries.last().startsWith("[${entries.size}/${entries.size}] "))
    }

    @Test
    fun `an unknown persisted limit is not mapped to a guess`() {
        assertEquals(null, HttpLogLimit.fromBytes(123))
        assertEquals(HttpLogLimit.MB_2, HttpLogLimit.fromBytes(2 * 1024 * 1024))
    }
}
