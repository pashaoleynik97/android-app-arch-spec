package {{ROOT_PACKAGE}}.network.log

/**
 * Splits one HTTP log message into entries logcat will actually keep.
 *
 * logd drops whatever does not fit in a **single entry** (about 4 KB of *bytes*, tag and the Reporter's own
 * prefix included), and OkHttp's logging interceptor hands a whole body over as one message — which is why a long
 * response looks "cut at about 4K characters". The budget here is counted in UTF-8 bytes, not characters: a body
 * in a script that needs two or three bytes a character would still be cut by a character budget.
 *
 * Pure and stateless, so the whole truncation contract is unit-tested without a device.
 */
object HttpLogChunker {

    /**
     * Bytes of message one entry may carry. The rest of logd's ~4068-byte entry is left for the tag, the
     * priority byte and the Reporter prefix, none of which this object can see.
     */
    const val ENTRY_BUDGET: Int = 3_500

    /** Room for the `[nnn/nnn] ` marker of a split part, reserved before the part count is known. */
    private const val PREFIX_RESERVE = 14

    /**
     * The entries to log for [message] under [policy], in order. A message that already fits is returned
     * **unchanged and unmarked** — the overwhelming majority of HTTP log lines (request lines, headers, short
     * bodies) must look exactly as OkHttp wrote them.
     */
    fun chunk(message: String, policy: HttpLogPolicy): List<String> {
        if (fitsInOneEntry(message)) return listOf(message)
        return if (policy.printFullBodies) full(message, policy.limit) else truncated(message)
    }

    /** Off: one entry, cut where logcat would have cut it, plus how much was dropped and how to get the rest. */
    private fun truncated(message: String): List<String> {
        // The hint's own size is reserved up front with the widest count it could print, so the finished entry
        // is guaranteed to fit rather than being cut again by logd.
        val reserved = utf8Length(truncatedHint(message.length))
        val head = takeUtf8(message, ENTRY_BUDGET - reserved)
        return listOf(head + truncatedHint(message.length - head.length))
    }

    /** On: the message across as many entries as it takes, bounded by [limit]. */
    private fun full(message: String, limit: HttpLogLimit): List<String> {
        val body = takeUtf8(message, limit.bytes)
        val overflowed = body.length < message.length
        val parts = splitUtf8(body, ENTRY_BUDGET - PREFIX_RESERVE)
        return buildList(parts.size + 1) {
            parts.forEachIndexed { index, part -> add("[${index + 1}/${parts.size}] $part") }
            if (overflowed) add(limitHint(limit))
        }
    }

    private fun truncatedHint(remaining: Int): String =
        " … <<truncated, $remaining more chars — set HttpLogPolicy.printFullBodies to print whole bodies>>"

    private fun limitHint(limit: HttpLogLimit): String =
        "<<body truncated at ${limit.label} — raise HttpLogPolicy.limit to print more>>"

    /** Cheap enough for every HTTP line: it walks the string only when the answer is in doubt. */
    private fun fitsInOneEntry(message: String): Boolean = when {
        // Nothing encodes to more than three bytes a char (a surrogate pair is two chars, four bytes).
        message.length <= ENTRY_BUDGET / 3 -> true
        message.length > ENTRY_BUDGET -> false
        else -> utf8Length(message) <= ENTRY_BUDGET
    }

    private fun splitUtf8(source: String, maxBytes: Int): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        while (start < source.length) {
            val end = takeUtf8(source, from = start, maxBytes = maxBytes)
            parts += source.substring(start, end)
            start = end
        }
        return parts
    }

    private fun takeUtf8(source: String, maxBytes: Int): String =
        source.substring(0, takeUtf8(source, from = 0, maxBytes = maxBytes))

    /** The index one past the last char from [from] that fits in [maxBytes]; never splits a surrogate pair. */
    private fun takeUtf8(source: String, from: Int, maxBytes: Int): Int {
        var bytes = 0
        var index = from
        while (index < source.length) {
            val pair = isPairAt(source, index)
            val cost = if (pair) 4 else utf8Cost(source[index])
            if (bytes + cost > maxBytes) break
            bytes += cost
            index += if (pair) 2 else 1
        }
        // A budget too small for even one character would loop forever upstream; one char over is still better
        // than an entry logd refuses.
        return if (index == from && from < source.length) from + 1 else index
    }

    private fun utf8Length(value: String): Int {
        var bytes = 0
        var index = 0
        while (index < value.length) {
            if (isPairAt(value, index)) {
                bytes += 4
                index += 2
            } else {
                bytes += utf8Cost(value[index])
                index++
            }
        }
        return bytes
    }

    private fun isPairAt(source: String, index: Int): Boolean =
        source[index].isHighSurrogate() && index + 1 < source.length && source[index + 1].isLowSurrogate()

    /** A lone surrogate counts as three bytes — that is what it encodes to (U+FFFD). */
    private fun utf8Cost(char: Char): Int = when {
        char.code < 0x80 -> 1
        char.code < 0x800 -> 2
        else -> 3
    }
}
