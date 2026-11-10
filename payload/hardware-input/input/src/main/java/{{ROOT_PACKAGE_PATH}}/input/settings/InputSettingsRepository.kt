package {{ROOT_PACKAGE}}.input.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import {{ROOT_PACKAGE}}.input.InputPreference
import {{ROOT_PACKAGE}}.input.di.InputScope
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's live view of the input preference.
 *
 * [preference] is **nullable on purpose**: `null` means *not read yet*, and the controller ignores it. Seeding the
 * flow with `Auto` instead would make the controller claim a device on every cold start and release it a moment
 * later when the persisted `Disabled` / `Forced` value arrives — a visible claim/release flicker for any other app
 * holding the hardware.
 */
@Singleton
class InputSettingsRepository @Inject constructor(
    private val store: InputSettingsStore,
    @param:InputScope private val scope: CoroutineScope,
) {

    private val logTag = logTag()

    private val mPreference = MutableStateFlow<InputPreference?>(null)

    /** `null` until the persisted value has been read. */
    val preference: StateFlow<InputPreference?> = mPreference.asStateFlow()

    init {
        scope.launch {
            val loaded = store.read()
            // A choice made while the read was in flight wins over the stale stored value.
            if (mPreference.value == null) mPreference.value = loaded
            Reporter.debug("input preference loaded: $loaded", where = logTag)
        }
    }

    /** Applies immediately (the controller swaps the driver) and persists in the background — optimistic. */
    fun setPreference(preference: InputPreference) {
        mPreference.value = preference
        scope.launch { store.write(preference) }
        Reporter.debug("input preference set: $preference", where = logTag)
    }
}
