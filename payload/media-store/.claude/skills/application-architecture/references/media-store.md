# Media store — device-local scratch media (component: media-store)

`:media` (package `{{ROOT_PACKAGE}}.media`, infra: no UI, no network) keeps captured media as **working material**:
private to the app, kept across process death, deleted when no longer needed, swept after a TTL. Spec:
`.arch-spec/spec/specs/recommended/media-store.md` (MEDIA-*).

Contents: 1. Surface · 2. Layout · 3. Rules · 4. Usage · 5. Testing

---

## 1. Surface

```kotlin
interface LocalMediaStore {
    suspend fun createFile(extension: String = "jpg"): MediaFile          // MediaFile(path, capturedAtMillis)
    suspend fun delete(paths: Collection<String>)                          // discard; a missing path is fine
    suspend fun keep(path: String, albumId: String, mediaId: String): String?  // publish → keep under server id
    suspend fun album(albumId: String): Map<String, String>                // server id → path
    suspend fun deleteAlbum(albumId: String)
    suspend fun sweepStale(): Int
}
class MediaDeletionScheduler { fun scheduleDeletion(paths); fun scheduleAlbumDeletion(albumId) }  // app scope
class StaleMediaSweeper { fun start() }       // once per process; started by MediaStartupInitializer
data class MediaStoreConfig(val staleAfterMillis: Long)   // provided by :app (MediaConfigModule)
fun interface MediaClock { fun nowMillis(): Long }        // wall clock on purpose
fun interface MediaDirectory { fun get(): File }          // filesDir/media, the only Context read
```

Every `suspend` method is main-safe (own `@MediaIoDispatcher`, from the kernel's `DispatcherProvider.io`).

## 2. Layout

```
filesDir/media/
  media_<capturedAt>_<uuid>.<ext>            pending — a capture writes here; the screen that made it owns it
  albums/<albumId>/
    media_<capturedAt>_<mediaId>.<ext>       kept — published, named after the id the server assigned
```

## 3. Rules

**MEDIA-01** — Captures live in `filesDir` (folder `media`): never the cache dir, never shared storage. *Why:* the cache can be
evicted before upload; shared storage is scanned into the gallery.
<!-- origin: arch-spec MEDIA-01 -->

**MEDIA-02** — The capture time is in the file name; an unparseable name falls back to `lastModified()`. *Why:* the
sweep needs no database, and an unreadable name must not make a file immortal.
<!-- origin: arch-spec MEDIA-02 -->

**MEDIA-03** — TTL uses the wall clock (`MediaClock`), not `TimeSource`. *Why:* the age must survive a reboot.
<!-- origin: arch-spec MEDIA-03 -->

**MEDIA-04** — `media/` is excluded from Auto Backup and device transfer (backup + data-extraction rules). *Why:*
`filesDir` is backed up by default; a backup outlives every deletion here.
<!-- origin: arch-spec MEDIA-04 -->

**MEDIA-05** — Publish = `keep` under the server's id (capture time carried over); a failed keep deletes the source;
ids are sanitised before they become paths. *Why:* look-ups by id, no unowned leftovers, no writes outside the store.
<!-- origin: arch-spec MEDIA-05 -->

**MEDIA-06** — Clean up where files are used; deletions requested by a closing screen go through
`MediaDeletionScheduler`. *Why:* a dying `viewModelScope` cancels the delete half-way.
<!-- origin: arch-spec MEDIA-06 -->

**MEDIA-07** — One TTL sweep per process at startup, over pending files and albums, never throwing. *Why:* the
backstop for crashes, killed processes and work finished elsewhere.
<!-- origin: arch-spec MEDIA-07 -->

**MEDIA-08** — The sweep is started by `MediaStartupInitializer` (`androidx.startup`, merged from `:media`'s
manifest) through a Hilt entry point, not from `App`. *Why:* installing the component edits no Core code and the
sweep runs even when the process starts without UI. If the app removes `androidx.startup.InitializationProvider`,
call `StaleMediaSweeper.start()` from `App.onCreate` instead.
<!-- origin: arch-spec MEDIA-08 -->

**MEDIA-09** — `MediaStoreConfig` is a host-provided data class (`app/…/di/MediaConfigModule.kt`). *Why:* the TTL is a
product decision tests may shrink; a value class over `Long` is not injectable.
<!-- origin: arch-spec MEDIA-09 -->

## 4. Usage

```kotlin
// capture screen (VM): get a target file, let the camera write it
val target = mediaStore.createFile("jpg")                    // hand target.path to CameraX OutputFileOptions

// publish (domain): one upload per file, so the response id belongs to exactly this file
when (val r = service.upload(File(target.path))) {
    is UploadResult.Success -> mediaStore.keep(target.path, albumId = orderId, mediaId = r.id)
    is UploadResult.Failure -> Unit                          // stays pending; retry later or discard
}

// discard (VM that is closing)
deletionScheduler.scheduleDeletion(pendingPaths)

// work finished (domain)
deletionScheduler.scheduleAlbumDeletion(orderId)

// show what was captured for the server's items
val local: Map<String, String> = mediaStore.album(orderId)  // serverId → path; fall back to the remote URL
```

Feature modules depend on `:media` from whichever end they need; the store never learns what an album means.

## 5. Testing

**MEDIA-10** — Test the store against a temp folder (JUnit temporary-folder rule) with a mutable `now` and an unconfined
test dispatcher. *Why:* file rules regress silently; real files make the tests honest.
<!-- origin: arch-spec MEDIA-10 -->

Reference suite: `media/src/test/…/InternalMediaStoreTest.kt` (name round-trip, TTL edges, unparseable names, missing
directory, albums, path escapes), `StaleMediaSweeperTest` (once per process, failure reported), and
`MediaDeletionSchedulerTest` (a deletion survives its caller's scope). Features fake `LocalMediaStore`
(`FakeLocalMediaStore` shows the shape) rather than touching disk.
