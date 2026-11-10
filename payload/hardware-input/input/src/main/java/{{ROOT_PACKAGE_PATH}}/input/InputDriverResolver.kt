package {{ROOT_PACKAGE}}.input

import {{ROOT_PACKAGE}}.input.api.Detection
import {{ROOT_PACKAGE}}.input.api.InputDriver
import {{ROOT_PACKAGE}}.input.api.InputDriverProvider
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns an [InputPreference] into a driver, using the providers Hilt multibound into this set (one per vendor
 * module, plus the debug driver in debug builds). Pure JVM: providers do their own `Context` work.
 */
@Singleton
class InputDriverResolver @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards InputDriverProvider>,
) {

    private val logTag = logTag()

    /** Every registered provider with its verdict, highest priority first — what a settings screen shows. */
    fun detectAll(): List<Pair<InputDriverProvider, Detection>> =
        providers.sortedByDescending { it.priority }.map { it to it.detect() }

    fun resolve(preference: InputPreference): InputDriver? = when (preference) {
        InputPreference.Disabled -> null

        is InputPreference.Forced -> {
            val forced = providers.firstOrNull { it.id == preference.id }
            if (forced != null) {
                forced.create()
            } else {
                // The persisted id names a vendor module this build no longer contains. Falling back beats leaving
                // the device with no input at all, but it must not be silent.
                Reporter.error(
                    "forced input driver '${preference.id.value}' is not registered; falling back to Auto",
                    where = logTag,
                )
                resolve(InputPreference.Auto)
            }
        }

        InputPreference.Auto ->
            detectAll().firstOrNull { (_, detection) -> detection is Detection.Supported }?.first?.create()
    }
}
