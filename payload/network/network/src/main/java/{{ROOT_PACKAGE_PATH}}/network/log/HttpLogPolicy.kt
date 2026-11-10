package {{ROOT_PACKAGE}}.network.log

/**
 * How much of an HTTP log message reaches logcat.
 *
 * Logcat drops whatever does not fit in a single entry, so a long body is cut unless it is split first — see
 * [HttpLogChunker]. Splitting costs a burst of entries in a ring buffer the whole device shares, so it is off
 * unless a developer turns it on (for example from a diagnostics screen, through [HttpLogSettingsRepository]).
 */
data class HttpLogPolicy(
    /** `false` (the default): one entry per message, cut where logcat would cut it, with a hint saying so. */
    val printFullBodies: Boolean = false,
    /** Ceiling on one message when [printFullBodies] is on. */
    val limit: HttpLogLimit = HttpLogLimit.DEFAULT,
)

/**
 * The selectable ceilings for a fully printed message.
 *
 * The cap protects **logcat's ring buffer**, not memory: OkHttp has already materialised the body by the time
 * the logger sees it. Above [KB_512] a single body can flush the buffer on a stock device unless the developer
 * raised the logger buffer size in the device's developer options.
 */
enum class HttpLogLimit(val bytes: Int, val label: String) {
    KB_512(512 * 1024, "512 KB"),
    MB_1(1024 * 1024, "1 MB"),
    MB_2(2 * 1024 * 1024, "2 MB"),
    MB_6(6 * 1024 * 1024, "6 MB"),
    ;

    companion object {
        val DEFAULT: HttpLogLimit = KB_512

        /** `null` for a value this build does not know (written by a newer build, or corrupt) — the caller decides. */
        fun fromBytes(bytes: Int): HttpLogLimit? = entries.firstOrNull { it.bytes == bytes }
    }
}
