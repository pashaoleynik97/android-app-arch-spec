package {{ROOT_PACKAGE}}.auth.token

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import {{ROOT_PACKAGE}}.libcommon.coroutines.DispatcherProvider
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/**
 * [SecureTokenStore] with AES-256/GCM under an **Android Keystore** key (generated on the device, never
 * exported, never derived from anything in the APK). Ciphertext is stored as Base64 `[iv | ciphertext+tag]` in a
 * dedicated SharedPreferences file, written with `commit()` inside the IO dispatcher.
 *
 * Hand-rolled on purpose instead of `EncryptedSharedPreferences` (deprecated, and its failure modes on key loss
 * are hard to control): the whole crypto surface is the two functions below, and every failure degrades to
 * "no session".
 *
 * [PREFS_FILE_NAME] MUST stay excluded from Auto Backup and device transfer (`app/src/main/res/xml/backup_rules.xml`
 * and `data_extraction_rules.xml`): restored onto another device, the ciphertext can never be decrypted — the
 * Keystore key does not travel.
 */
internal class KeystoreSecureTokenStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : SecureTokenStore {

    private val logTag = logTag()

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    override suspend fun currentUserId(): String? = withContext(dispatchers.io) {
        prefs.getString(KEY_CURRENT_USER, null)
    }

    override suspend fun readRefreshToken(userId: String): String? = withContext(dispatchers.io) {
        val stored = prefs.getString(refreshKey(userId), null) ?: return@withContext null
        try {
            decrypt(stored)
        } catch (e: GeneralSecurityException) {
            // Key invalidated (lock-screen reset, restored backup) or tampered ciphertext.
            Reporter.warn("refresh token undecryptable; treating as no session", exception = e, where = logTag)
            null
        } catch (e: java.security.ProviderException) {
            // Some Keystore implementations report a broken key this way instead of a checked exception.
            Reporter.warn("keystore refused the key; treating as no session", exception = e, where = logTag)
            null
        } catch (e: IllegalArgumentException) {
            // Not Base64 / too short to hold an IV.
            Reporter.warn("refresh token unreadable; treating as no session", exception = e, where = logTag)
            null
        }
    }

    override suspend fun save(userId: String, refreshToken: String) = withContext(dispatchers.io) {
        prefs.edit()
            .putString(refreshKey(userId), encrypt(refreshToken))
            .putString(KEY_CURRENT_USER, userId)
            .commit()
        Unit
    }

    override suspend fun clear() = withContext(dispatchers.io) {
        prefs.edit().clear().commit()
        Unit
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > GCM_IV_LENGTH) { "ciphertext too short" }
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun refreshKey(userId: String) = "$KEY_REFRESH_PREFIX$userId"

    companion object {
        /** Keep in sync with the backup exclusions in `app/src/main/res/xml/`. */
        const val PREFS_FILE_NAME = "auth_secure_prefs"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "session_refresh_token_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val KEY_CURRENT_USER = "current_user"
        private const val KEY_REFRESH_PREFIX = "rt_"
    }
}
