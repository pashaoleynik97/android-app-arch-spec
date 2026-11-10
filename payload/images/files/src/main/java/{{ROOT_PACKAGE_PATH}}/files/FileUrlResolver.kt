package {{ROOT_PACKAGE}}.files

import {{ROOT_PACKAGE}}.files.config.FilesConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds absolute URLs for server-stored files from the bare file name an API returns (`"u42-mini.jpg"`).
 * Domain code exposes these URLs; screens hand them to `{{THEME_PREFIX}}RemoteImage` / `{{THEME_PREFIX}}Avatar`, which
 * fetch through the **authenticated** files client (see `FilesModule`).
 */
@Singleton
class FileUrlResolver @Inject constructor(private val config: FilesConfig) {

    /** Absolute URL for [fileName], e.g. `https://host/v1/files/u42-mini.jpg`. The name is URL-path-encoded. */
    fun fileUrl(fileName: String): String =
        config.baseUrl.trimEnd('/') + "/" + config.filesPath.trim('/') + "/" + encodePathSegment(fileName.trimStart('/'))

    /** [fileUrl] for an optional name; `null` for a null or blank one, so the UI shows its placeholder. */
    fun fileUrlOrNull(fileName: String?): String? = fileName?.takeIf { it.isNotBlank() }?.let(::fileUrl)

    /** Percent-encodes everything outside the unreserved set, keeping `/` so nested names stay paths. */
    private fun encodePathSegment(value: String): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val c = byte.toInt().toChar()
            if (byte >= 0 && (c.isLetterOrDigit() || c in "-._~/")) append(c) else append("%%%02X".format(byte.toInt() and 0xFF))
        }
    }
}
