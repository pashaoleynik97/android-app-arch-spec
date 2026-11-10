package {{ROOT_PACKAGE}}.media

/** Records what was asked of the store; [sweepFailure] makes the next sweep throw. */
class FakeLocalMediaStore : LocalMediaStore {

    val deleted = mutableListOf<List<String>>()
    val deletedAlbums = mutableListOf<String>()
    var sweepCalls = 0
        private set
    var sweepFailure: Throwable? = null

    override suspend fun createFile(extension: String) = MediaFile(path = "/fake/media.$extension", capturedAtMillis = 0)

    override suspend fun delete(paths: Collection<String>) {
        deleted += paths.toList()
    }

    override suspend fun keep(path: String, albumId: String, mediaId: String): String? = null

    override suspend fun album(albumId: String): Map<String, String> = emptyMap()

    override suspend fun deleteAlbum(albumId: String) {
        deletedAlbums += albumId
    }

    override suspend fun sweepStale(): Int {
        sweepCalls++
        sweepFailure?.let { throw it }
        return 0
    }
}
