package {{ROOT_PACKAGE}}.auth.token

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import {{ROOT_PACKAGE}}.auth.SessionUser
import {{ROOT_PACKAGE}}.libcommon.coroutines.DispatcherProvider
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import javax.inject.Inject

/**
 * [ProfileStore] as one JSON blob per user (`profile_<userId>`) in a dedicated, **unencrypted** preferences file
 * — profile fields are not secrets, and keeping them out of the encrypted file keeps that one refresh-token-only.
 * [PREFS_FILE_NAME] is excluded from Auto Backup next to the token file (a restored profile without its session
 * would show a stranger's name on a new device).
 */
internal class SharedPrefsProfileStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val json: Json,
) : ProfileStore {

    private val logTag = logTag()

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    override suspend fun read(userId: String): SessionUser? = withContext(dispatchers.io) {
        val stored = prefs.getString(profileKey(userId), null) ?: return@withContext null
        try {
            json.decodeFromString<ProfileRecord>(stored).toDomain()
        } catch (e: SerializationException) {
            Reporter.warn("profile record unparseable; treating as absent", exception = e, where = logTag)
            null
        }
    }

    override suspend fun save(user: SessionUser) = withContext(dispatchers.io) {
        prefs.edit().putString(profileKey(user.id), json.encodeToString(ProfileRecord.serializer(), user.toRecord())).commit()
        Unit
    }

    override suspend fun clear(userId: String) = withContext(dispatchers.io) {
        prefs.edit().remove(profileKey(userId)).commit()
        Unit
    }

    private fun profileKey(userId: String) = "profile_$userId"

    companion object {
        /** Keep in sync with the backup exclusions in `app/src/main/res/xml/`. */
        const val PREFS_FILE_NAME = "auth_profile_prefs"
    }
}

/** On-disk shape, kept off the public model so the model can change without a migration. */
@Serializable
internal data class ProfileRecord(val id: String, val email: String, val displayName: String)

private fun ProfileRecord.toDomain() = SessionUser(id, email, displayName)

private fun SessionUser.toRecord() = ProfileRecord(id, email, displayName)
