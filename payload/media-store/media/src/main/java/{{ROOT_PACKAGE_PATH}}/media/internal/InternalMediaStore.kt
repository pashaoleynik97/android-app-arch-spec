package {{ROOT_PACKAGE}}.media.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import {{ROOT_PACKAGE}}.media.LocalMediaStore
import {{ROOT_PACKAGE}}.media.MediaClock
import {{ROOT_PACKAGE}}.media.MediaDirectory
import {{ROOT_PACKAGE}}.media.MediaFile
import {{ROOT_PACKAGE}}.media.MediaStoreConfig
import {{ROOT_PACKAGE}}.media.di.MediaIoDispatcher
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LocalMediaStore] over a directory inside the application's internal files dir.
 *
 * `filesDir/<dir>` — **not** the cache dir and not external storage. The cache is the wrong home because Android
 * may evict it between the capture and the upload, silently losing the user's work; external storage is wrong
 * because the media scanner would index the files into the device gallery. `filesDir` is private, never scanned,
 * and needs no `.nomedia` trick. (It *is* included in Auto Backup unless excluded — see the component's install
 * notes.)
 *
 * ```
 * <dir>/
 *   media_<capturedAt>_<uuid>.<ext>          pending — written by a capture, owned by the screen that made it
 *   albums/<albumId>/
 *     media_<capturedAt>_<mediaId>.<ext>     kept — published, named after the id the server assigned
 * ```
 *
 * Both areas carry the capture time in the **file name**, which is what lets one sweep cover both with no database.
 * Every entry point switches to its own IO dispatcher, so callers (ViewModels included) stay main-safe.
 */
@Singleton
internal class InternalMediaStore @Inject constructor(
    private val directory: MediaDirectory,
    private val config: MediaStoreConfig,
    private val clock: MediaClock,
    @param:MediaIoDispatcher private val io: CoroutineDispatcher,
) : LocalMediaStore {

    private val logTag = logTag()

    override suspend fun createFile(extension: String): MediaFile = withContext(io) {
        val dir = directory.get()
        if (!dir.isDirectory && !dir.mkdirs()) {
            error("Cannot create media directory ${dir.absolutePath}")
        }
        val capturedAt = clock.nowMillis()
        val unique = UUID.randomUUID().toString().replace("-", "")
        val file = File(dir, "$PREFIX$capturedAt$SEPARATOR$unique.${extension.safeExtension()}")
        file.createNewFile()
        MediaFile(path = file.absolutePath, capturedAtMillis = capturedAt)
    }

    override suspend fun delete(paths: Collection<String>) {
        if (paths.isEmpty()) return
        withContext(io) {
            paths.forEach { path ->
                runCatching { File(path).delete() }
                    .onFailure { Reporter.error("failed to delete $path", exception = it, where = logTag) }
            }
        }
    }

    override suspend fun keep(path: String, albumId: String, mediaId: String): String? = withContext(io) {
        val source = File(path)
        if (!source.isFile) {
            Reporter.warn("cannot keep a file that is gone: $path", where = logTag)
            return@withContext null
        }

        val album = albumDir(albumId)
        if (!album.isDirectory && !album.mkdirs()) {
            Reporter.error("cannot create album ${album.absolutePath}", where = logTag)
            source.delete()
            return@withContext null
        }

        // The capture time is carried over rather than re-read from the clock: a kept file's age is still the age
        // of the capture, which is what the sweep is entitled to measure.
        val name = "$PREFIX${source.capturedAtMillis()}$SEPARATOR${mediaId.fileSafe()}.${source.extension.safeExtension()}"
        val target = File(album, name)
        val moved = runCatching { source.renameTo(target) }.getOrDefault(false) ||
            // A rename inside one directory tree does not cross filesystems, so this is defence against the exotic
            // (a vendor overlay, a name the platform dislikes) rather than the usual path.
            runCatching { source.copyTo(target, overwrite = true) }.isSuccess

        if (!moved) {
            Reporter.error("failed to keep $path in album $albumId", where = logTag)
            source.delete()
            return@withContext null
        }
        // A no-op after a successful rename; after the copy fallback it removes the original.
        source.delete()
        target.absolutePath
    }

    override suspend fun album(albumId: String): Map<String, String> = withContext(io) {
        albumDir(albumId).listFiles().orEmpty()
            .filter { it.isFile }
            .mapNotNull { file -> file.mediaId()?.let { id -> id to file.absolutePath } }
            .toMap()
    }

    override suspend fun deleteAlbum(albumId: String) {
        withContext(io) {
            val album = albumDir(albumId)
            if (!album.exists()) return@withContext
            runCatching { album.deleteRecursively() }
                .onFailure { Reporter.error("failed to delete album $albumId", exception = it, where = logTag) }
        }
    }

    override suspend fun sweepStale(): Int = withContext(io) {
        val root = directory.get()
        val deadline = clock.nowMillis() - config.staleAfterMillis
        var deleted = sweep(root.listFiles().orEmpty(), deadline)

        albumsRoot().listFiles().orEmpty().filter { it.isDirectory }.forEach { album ->
            deleted += sweep(album.listFiles().orEmpty(), deadline)
            // An album nobody discarded and whose files have all aged out is itself litter.
            if (album.listFiles().orEmpty().isEmpty()) album.delete()
        }

        if (deleted > 0) Reporter.debug("swept $deleted stale file(s)", where = logTag)
        deleted
    }

    /** Deletes the stale files among [files]; directories (the albums root included) are left alone. */
    private fun sweep(files: Array<out File>, deadline: Long): Int {
        var deleted = 0
        files.forEach { file ->
            val stale = file.isFile && file.capturedAtMillis() <= deadline
            if (stale && runCatching { file.delete() }.getOrDefault(false)) deleted++
        }
        return deleted
    }

    private fun albumsRoot(): File = File(directory.get(), ALBUMS_DIR)

    private fun albumDir(albumId: String): File = File(albumsRoot(), albumId.fileSafe())

    /**
     * The timestamp imprinted in the name, or the file system's own if the name is not ours. The fallback matters:
     * an unparseable name must not make a file immortal — exactly the leak this sweep exists to close.
     */
    private fun File.capturedAtMillis(): Long =
        name.takeIf { it.startsWith(PREFIX) }
            ?.removePrefix(PREFIX)
            ?.substringBefore(SEPARATOR)
            ?.toLongOrNull()
            ?: lastModified()

    /**
     * The server media id a kept file is named after, or `null` if the name is not ours. Read as "everything
     * between the timestamp and the extension", so an id that itself contains the separator still round-trips.
     */
    private fun File.mediaId(): String? =
        name.takeIf { it.startsWith(PREFIX) }
            ?.removePrefix(PREFIX)
            ?.substringBeforeLast('.')
            ?.substringAfter(SEPARATOR, missingDelimiterValue = "")
            ?.takeIf { it.isNotEmpty() }

    /**
     * Keeps an id the caller chose from becoming a path. Server ids are usually uuids, so in practice this is the
     * identity function — it exists so that a stranger id gets a mangled name (at worst a lookup that misses)
     * rather than a write outside the store.
     */
    private fun String.fileSafe(): String = map { if (it in ALLOWED_ID_CHARS) it else '-' }.joinToString("")

    private fun String.safeExtension(): String =
        filter { it.isLetterOrDigit() && it.code < 128 }.ifEmpty { LocalMediaStore.DEFAULT_EXTENSION }

    private companion object {
        const val PREFIX = "media_"
        const val SEPARATOR = "_"

        /** Sits beside the pending files; it cannot collide with one, since those all start with [PREFIX]. */
        const val ALBUMS_DIR = "albums"

        val ALLOWED_ID_CHARS = (('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('-', '_')).toSet()
    }
}
