package {{ROOT_PACKAGE}}.media

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import {{ROOT_PACKAGE}}.media.internal.InternalMediaStore
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The store's whole job is that no capture outlives its usefulness, so these tests are mostly about deletion:
 * what the sweep takes, what it leaves, and what happens to a file whose name it cannot read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InternalMediaStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val dir: File by lazy { temp.newFolder("media") }
    private var now: Long = 1_700_000_000_000L
    private val ttl = TimeUnit.HOURS.toMillis(12)

    private fun store() = InternalMediaStore(
        directory = { dir },
        config = MediaStoreConfig(ttl),
        clock = { now },
        io = UnconfinedTestDispatcher(),
    )

    // region pending files

    @Test
    fun `a created file lands in the directory and carries its capture time in the name`() = runTest {
        val media = store().createFile()

        val file = File(media.path)
        assertTrue(file.exists())
        assertEquals(dir.absolutePath, file.parentFile?.absolutePath)
        assertEquals(now, media.capturedAtMillis)
        assertTrue("the name should imprint the timestamp", file.name.startsWith("media_${now}_"))
        assertTrue(file.name.endsWith(".jpg"))
    }

    @Test
    fun `the extension is honoured and sanitised`() = runTest {
        assertTrue(store().createFile("mp4").path.endsWith(".mp4"))
        assertTrue(store().createFile("../x").path.endsWith(".x"))
        assertTrue("an empty extension falls back to the default", store().createFile("").path.endsWith(".jpg"))
    }

    @Test
    fun `two files created in the same millisecond do not collide`() = runTest {
        val store = store()
        val first = store.createFile()
        val second = store.createFile()

        assertTrue(first.path != second.path)
        assertEquals(2, dir.listFiles()!!.size)
    }

    @Test
    fun `delete removes the files and tolerates a path that is already gone`() = runTest {
        val store = store()
        val media = store.createFile()

        store.delete(listOf(media.path, File(dir, "never-existed.jpg").absolutePath))

        assertFalse(File(media.path).exists())
    }

    // endregion

    // region sweep

    @Test
    fun `sweep takes files older than the ttl and leaves the rest`() = runTest {
        val store = store()
        val old = store.createFile()
        now += TimeUnit.HOURS.toMillis(11)
        val fresh = store.createFile()
        // 12h after the first capture, 1h after the second.
        now += TimeUnit.HOURS.toMillis(1)

        assertEquals(1, store.sweepStale())
        assertFalse(File(old.path).exists())
        assertTrue(File(fresh.path).exists())
    }

    @Test
    fun `a file exactly at the ttl is swept`() = runTest {
        val store = store()
        val media = store.createFile()
        now += ttl

        assertEquals(1, store.sweepStale())
        assertFalse(File(media.path).exists())
    }

    @Test
    fun `a file one millisecond younger than the ttl survives`() = runTest {
        val store = store()
        val media = store.createFile()
        now += ttl - 1

        assertEquals(0, store.sweepStale())
        assertTrue(File(media.path).exists())
    }

    @Test
    fun `a file whose name is not ours falls back to its last-modified time`() = runTest {
        val stranger = File(dir, "IMG_0001.jpg").apply {
            createNewFile()
            setLastModified(now - ttl - 1_000)
        }
        // An unparseable name must not make a file immortal — the exact leak the sweep exists for.
        assertEquals(1, store().sweepStale())
        assertFalse(stranger.exists())
    }

    @Test
    fun `sweeping an empty or missing directory is a no-op`() = runTest {
        assertEquals(0, store().sweepStale())

        dir.delete()
        assertEquals(0, store().sweepStale())
    }

    // endregion

    // region albums

    @Test
    fun `keep moves the file into the album under the server id and keeps the capture time`() = runTest {
        val store = store()
        val pending = store.createFile()

        val kept = store.keep(pending.path, albumId = "album-1", mediaId = "srv-42")

        assertFalse("the pending copy must not survive", File(pending.path).exists())
        assertTrue(File(kept!!).exists())
        assertEquals(File(dir, "albums/album-1").absolutePath, File(kept).parentFile?.absolutePath)
        assertEquals("media_${now}_srv-42.jpg", File(kept).name)
    }

    @Test
    fun `album lists kept files by their server id`() = runTest {
        val store = store()
        store.keep(store.createFile().path, "album-1", "srv-1")
        store.keep(store.createFile().path, "album-1", "srv_2")
        store.keep(store.createFile().path, "album-2", "srv-3")

        val album = store.album("album-1")

        assertEquals("an id containing the separator still round-trips", setOf("srv-1", "srv_2"), album.keys)
        assertTrue(album.values.all { File(it).exists() })
        assertEquals(setOf("srv-3"), store.album("album-2").keys)
        assertTrue(store.album("never-used").isEmpty())
    }

    @Test
    fun `an id with path characters cannot escape the store`() = runTest {
        val store = store()
        val kept = store.keep(store.createFile().path, albumId = "../../etc", mediaId = "../x")!!

        assertTrue(File(kept).canonicalPath.startsWith(dir.canonicalPath))
    }

    @Test
    fun `keeping a file that is gone returns null and creates no album entry`() = runTest {
        val store = store()

        assertNull(store.keep(File(dir, "never-existed.jpg").absolutePath, "album-1", "srv-1"))
        assertTrue(store.album("album-1").isEmpty())
    }

    @Test
    fun `deleteAlbum removes every kept file and leaves the other albums alone`() = runTest {
        val store = store()
        val keptMine = store.keep(store.createFile().path, "album-1", "srv-1")!!
        store.keep(store.createFile().path, "album-2", "srv-2")

        store.deleteAlbum("album-1")
        // Deleting an album that was never created is not an error.
        store.deleteAlbum("never-used")

        assertFalse(File(keptMine).exists())
        assertTrue(store.album("album-1").isEmpty())
        assertEquals(1, store.album("album-2").size)
    }

    @Test
    fun `sweep takes stale kept files and removes the album they emptied`() = runTest {
        val store = store()
        store.keep(store.createFile().path, "album-1", "srv-1")
        now += TimeUnit.HOURS.toMillis(11)
        val keptFresh = store.keep(store.createFile().path, "album-2", "srv-2")!!
        now += TimeUnit.HOURS.toMillis(1)

        assertEquals(1, store.sweepStale())
        assertTrue(store.album("album-1").isEmpty())
        assertFalse("an emptied album is litter too", File(dir, "albums/album-1").exists())
        assertTrue(File(keptFresh).exists())
    }

    @Test
    fun `sweep leaves the albums directory itself alone`() = runTest {
        val store = store()
        store.keep(store.createFile().path, "album-1", "srv-1")
        now += ttl

        assertEquals(1, store.sweepStale())
        assertTrue("the root listing must not mistake the albums folder for a stale file", File(dir, "albums").isDirectory)
    }

    // endregion
}
