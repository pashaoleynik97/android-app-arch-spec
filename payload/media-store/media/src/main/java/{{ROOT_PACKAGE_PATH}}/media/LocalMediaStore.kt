package {{ROOT_PACKAGE}}.media

import java.io.File

/**
 * One captured file (a photo, a recording, a signature image) as it exists on this device.
 *
 * [path] is an absolute path inside the application's own storage — never a `content://` uri and never anything a
 * gallery app or a file manager can reach. [capturedAtMillis] is wall-clock time taken when the file was created,
 * and it is what the stale sweep measures against.
 */
data class MediaFile(
    val path: String,
    val capturedAtMillis: Long,
)

/**
 * The device-local **scratch** store for media the user captures as working material.
 *
 * These files are not documents, and everything here is about making sure none of them outlives its usefulness: a
 * shared device must not accumulate other people's captures, the user has no way to clean them up by hand, and a
 * crash or a killed process must not leave them behind forever.
 *
 * Two areas:
 * - the flat **pending** area a capture writes into ([createFile]), emptied when the capture is published or
 *   discarded ([delete]);
 * - **albums** ([keep]), where a published file's local copy is retained under the id the *server* gave it, so a
 *   later screen can still show what was captured without downloading it again. An album is deleted when the work
 *   it documents is finished ([deleteAlbum]).
 *
 * The TTL sweep ([sweepStale]) is the backstop for everything that reached neither cleanup.
 *
 * A standalone infra module rather than a feature's domain because several features need it from different ends
 * (one captures, one publishes and keeps, one reads and discards) and the sweep must run before any screen exists.
 */
interface LocalMediaStore {

    /**
     * Creates (and returns) an empty file for a capture to write into. The capture time is imprinted in the
     * **file name**, so an orphaned file still carries its own age even if nothing else about it survived — the
     * app's in-memory list of pending captures does not outlive the process, and the sweep must work without it.
     *
     * @param extension file extension without the dot (`jpg`, `mp4`, `png`); only `[A-Za-z0-9]` is kept.
     */
    suspend fun createFile(extension: String = DEFAULT_EXTENSION): MediaFile

    /** Deletes the given files (discard). A path that is already gone is not an error. */
    suspend fun delete(paths: Collection<String>)

    /**
     * Moves the file at [path] into [albumId] under the id the **server** gave it ([mediaId]) and returns its new
     * path (publish → keep).
     *
     * [mediaId] is what makes the copy useful later: a screen listing the server's media looks each one up by id
     * and finds the local file for exactly that item — no positional guessing. The capture time stays in the new
     * name, so the sweep reads a kept file exactly as it reads a pending one.
     *
     * Returns `null` when the file could not be kept (it is gone, or the move failed). A kept copy is a nicety,
     * never a correctness requirement, so a failure **deletes** the source rather than leaving it in the pending
     * area where nothing owns it any more.
     */
    suspend fun keep(path: String, albumId: String, mediaId: String): String?

    /** The local copies kept for [albumId], as server media id → absolute path. Empty when there are none. */
    suspend fun album(albumId: String): Map<String, String>

    /** Deletes [albumId] entirely — every file in it and the album directory itself. Unknown album: no-op. */
    suspend fun deleteAlbum(albumId: String)

    /**
     * Deletes every stored file older than the configured TTL — pending **and** kept — and returns how many went.
     * Removes album directories it empties.
     */
    suspend fun sweepStale(): Int

    companion object {
        const val DEFAULT_EXTENSION = "jpg"
    }
}

/**
 * How long a captured file may sit in the store before the sweep takes it. Provided by the host (`:app`), like
 * every config the host may need to shrink in a test build.
 *
 * A `data class`, not a `@JvmInline value class`: a value class over a `Long` erases to a primitive `long`, and
 * Dagger's KSP processor rejects a primitive as an injected type.
 */
data class MediaStoreConfig(val staleAfterMillis: Long) {
    init {
        require(staleAfterMillis > 0) { "staleAfterMillis must be positive, was $staleAfterMillis" }
    }
}

/**
 * Wall-clock seam. Deliberately **not** the kernel's monotonic `TimeSource`: "older than N hours" is a question
 * about the calendar and must stay true across a reboot, which a since-boot clock does not. Faked in tests.
 */
fun interface MediaClock {
    fun nowMillis(): Long
}

/**
 * Where the store lives on this device — resolved in the DI module from the application context.
 *
 * A seam rather than a `Context` read inside the store, so the store itself is a plain JVM class a unit test can
 * point at a temporary folder. Public because Dagger calls the `@Provides` that builds it.
 */
fun interface MediaDirectory {
    fun get(): File
}
