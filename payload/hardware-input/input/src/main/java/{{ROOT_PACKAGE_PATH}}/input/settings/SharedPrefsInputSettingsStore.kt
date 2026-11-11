package {{ROOT_PACKAGE}}.input.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import {{ROOT_PACKAGE}}.input.InputPreference
import {{ROOT_PACKAGE}}.input.InputPreferenceCodec
import {{ROOT_PACKAGE}}.input.di.InputIoDispatcher
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shipped [InputSettingsStore]: one plain `SharedPreferences` file. Nothing here is a secret — it is a
 * device-level override — so it does not go through encrypted storage, and it is **not** wiped on logout: the
 * hardware belongs to the device, not to whoever is signed in.
 */
@Singleton
internal class SharedPrefsInputSettingsStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:InputIoDispatcher private val io: CoroutineDispatcher,
) : InputSettingsStore {

    private val logTag = logTag()

    override suspend fun read(): InputPreference = withContext(io) {
        val raw = prefs().getString(KEY_PREFERENCE, null)
        InputPreferenceCodec.decode(raw) ?: InputPreference.Auto.also {
            // A value from a newer build, or a corrupt file. Auto keeps the device working; the engineer re-picks.
            Reporter.error("unrecognized persisted input preference '$raw'; using Auto", where = logTag)
        }
    }

    override suspend fun write(preference: InputPreference) = withContext(io) {
        prefs().edit { putString(KEY_PREFERENCE, InputPreferenceCodec.encode(preference)) }
    }

    private fun prefs() = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS_FILE_NAME = "input_settings"
        const val KEY_PREFERENCE = "input_preference"
    }
}
