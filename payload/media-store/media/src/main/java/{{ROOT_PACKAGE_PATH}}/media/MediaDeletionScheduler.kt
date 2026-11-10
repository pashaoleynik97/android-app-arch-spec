package {{ROOT_PACKAGE}}.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import {{ROOT_PACKAGE}}.media.di.MediaScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fire-and-forget deletion of captured files.
 *
 * Deletion is almost always requested at the exact moment the requester is dying: a screen pops right after a
 * successful upload or a finished flow, and a ViewModel's own scope is cancelled with it. Running the delete on an
 * application-lifetime scope makes "the files are gone" true rather than likely — and the caller does not wait for
 * the file system before it navigates.
 *
 * A deletion that still gets lost (process death mid-delete) is caught by [LocalMediaStore.sweepStale].
 */
@Singleton
class MediaDeletionScheduler @Inject constructor(
    private val store: LocalMediaStore,
    @param:MediaScope private val scope: CoroutineScope,
) {

    /** Schedules [paths] for deletion and returns immediately. */
    fun scheduleDeletion(paths: Collection<String>) {
        if (paths.isEmpty()) return
        val snapshot = paths.toList()
        scope.launch { store.delete(snapshot) }
    }

    /** Schedules the whole of [albumId] for deletion and returns immediately. */
    fun scheduleAlbumDeletion(albumId: String) {
        if (albumId.isEmpty()) return
        scope.launch { store.deleteAlbum(albumId) }
    }
}
