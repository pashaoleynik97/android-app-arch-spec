package {{ROOT_PACKAGE}}.network.log

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import {{ROOT_PACKAGE}}.libcommon.coroutines.DispatcherProvider
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import javax.inject.Inject

/**
 * The shipped [HttpLogSettingsStore]: one plain SharedPreferences file. Nothing here is a secret — it is a
 * device-local diagnostic switch — and it is deliberately **not** cleared on sign-out: a developer who turned full
 * logging on to chase a bug expects it to still be on after signing out and back in.
 */
internal class SharedPrefsHttpLogSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : HttpLogSettingsStore {

    private val logTag = logTag()

    override suspend fun read(): HttpLogPolicy = withContext(dispatchers.io) {
        val prefs = prefs()
        val rawLimit = prefs.getInt(KEY_LIMIT_BYTES, HttpLogLimit.DEFAULT.bytes)
        val limit = HttpLogLimit.fromBytes(rawLimit) ?: run {
            // Written by a newer build, or corrupt: the default keeps logging working (KERNEL-17).
            Reporter.warn("unknown persisted HTTP log limit $rawLimit bytes; using the default", where = logTag)
            HttpLogLimit.DEFAULT
        }
        HttpLogPolicy(printFullBodies = prefs.getBoolean(KEY_PRINT_FULL_BODIES, false), limit = limit)
    }

    override suspend fun write(policy: HttpLogPolicy) = withContext(dispatchers.io) {
        prefs().edit {
            putBoolean(KEY_PRINT_FULL_BODIES, policy.printFullBodies)
            putInt(KEY_LIMIT_BYTES, policy.limit.bytes)
        }
    }

    private fun prefs() = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS_FILE_NAME = "http_log_prefs"
        const val KEY_PRINT_FULL_BODIES = "print_full_bodies"
        const val KEY_LIMIT_BYTES = "full_body_limit_bytes"
    }
}
