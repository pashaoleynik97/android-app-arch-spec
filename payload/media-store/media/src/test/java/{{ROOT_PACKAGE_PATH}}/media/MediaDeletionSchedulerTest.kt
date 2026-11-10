package {{ROOT_PACKAGE}}.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDeletionSchedulerTest {

    private val store = FakeLocalMediaStore()

    @Test
    fun `a deletion requested by a dying caller still completes`() = runTest {
        val mediaScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val scheduler = MediaDeletionScheduler(store, mediaScope)
        // Stand-in for a viewModelScope that is cancelled right after asking.
        val callerScope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))

        callerScope.launch { scheduler.scheduleDeletion(listOf("/a", "/b")) }
        advanceUntilIdle()
        callerScope.cancel()
        advanceUntilIdle()

        assertEquals(listOf(listOf("/a", "/b")), store.deleted)
    }

    @Test
    fun `the paths are snapshotted when scheduled`() = runTest {
        val scheduler = MediaDeletionScheduler(store, CoroutineScope(StandardTestDispatcher(testScheduler)))
        val paths = mutableListOf("/a")

        scheduler.scheduleDeletion(paths)
        paths += "/late"
        advanceUntilIdle()

        assertEquals(listOf(listOf("/a")), store.deleted)
    }

    @Test
    fun `empty requests do nothing`() = runTest {
        val scheduler = MediaDeletionScheduler(store, CoroutineScope(StandardTestDispatcher(testScheduler)))

        scheduler.scheduleDeletion(emptyList())
        scheduler.scheduleAlbumDeletion("")
        advanceUntilIdle()

        assertTrue(store.deleted.isEmpty())
        assertTrue(store.deletedAlbums.isEmpty())
    }

    @Test
    fun `an album deletion is forwarded`() = runTest {
        val scheduler = MediaDeletionScheduler(store, CoroutineScope(StandardTestDispatcher(testScheduler)))

        scheduler.scheduleAlbumDeletion("album-1")
        advanceUntilIdle()

        assertEquals(listOf("album-1"), store.deletedAlbums)
    }
}
