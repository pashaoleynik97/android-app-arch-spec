package {{ROOT_PACKAGE}}.input.debug

import {{ROOT_PACKAGE}}.input.api.InputEvent

/**
 * The adb side of the debug driver, kept free of Android types so it is unit-tested:
 *
 * ```
 * adb shell am broadcast -a {{APPLICATION_ID}}.input.DEBUG_EVENT --es data "4006381333931" --es kind "ean13"
 * ```
 *
 * The action is built from the **installed package name** at runtime (not hard-coded), so a build with an
 * `applicationIdSuffix` still answers to its own id and two variants on one device never both react.
 */
object DebugInputEvents {

    const val ACTION_SUFFIX = ".input.DEBUG_EVENT"
    const val EXTRA_DATA = "data"
    const val EXTRA_KIND = "kind"

    fun action(packageName: String): String = packageName + ACTION_SUFFIX

    /**
     * Builds the event for one broadcast, or `null` when it carries no `data` extra (logged and ignored by the
     * driver — an empty reading would look like a real one to a screen). [kind] is lower-cased so `EAN13` and
     * `ean13` are the same kind.
     */
    fun toEvent(data: String?, kind: String?, nowMillis: Long): InputEvent? {
        if (data == null) return null
        val normalizedKind = kind?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return InputEvent(
            data = data,
            rawBytes = data.toByteArray(),
            kind = normalizedKind,
            source = DebugBroadcastInputProvider.ID,
            timestamp = nowMillis,
            extras = buildMap {
                put("injectedBy", "adb")
                kind?.let { put(EXTRA_KIND, it) }
            },
        )
    }
}
