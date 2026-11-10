package {{ROOT_PACKAGE}}.input

import {{ROOT_PACKAGE}}.input.api.InputDriverId

/** Which driver the device should use. Persisted by the input settings store; changed on a diagnostics screen. */
sealed interface InputPreference {

    /** Let detection decide — the highest-priority provider that reports its hardware present. */
    data object Auto : InputPreference

    /**
     * Use this provider **without** running detection — the manual override for a device where auto-detection
     * fails, which is the reason a settings screen exists at all.
     */
    data class Forced(val id: InputDriverId) : InputPreference

    /** No driver at all. */
    data object Disabled : InputPreference
}

/**
 * The persisted form: one string, so a new vendor never needs a schema change —
 * `"auto"` | `"disabled"` | `"forced:<driverId>"`.
 */
object InputPreferenceCodec {

    private const val AUTO = "auto"
    private const val DISABLED = "disabled"
    private const val FORCED_PREFIX = "forced:"

    fun encode(preference: InputPreference): String = when (preference) {
        InputPreference.Auto -> AUTO
        InputPreference.Disabled -> DISABLED
        is InputPreference.Forced -> FORCED_PREFIX + preference.id.value
    }

    /**
     * `null` (nothing stored) reads as [InputPreference.Auto]. An unrecognized value — written by a newer build or
     * a corrupt file — returns `null` from this function so the caller can log it and fall back to `Auto`.
     */
    fun decode(raw: String?): InputPreference? = when {
        raw == null || raw == AUTO -> InputPreference.Auto
        raw == DISABLED -> InputPreference.Disabled
        raw.startsWith(FORCED_PREFIX) && raw.length > FORCED_PREFIX.length ->
            InputPreference.Forced(InputDriverId(raw.removePrefix(FORCED_PREFIX)))
        else -> null
    }
}
