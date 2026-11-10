# Recommended: media-store — device-local scratch media (MEDIA)

Tier: **Recommended** (component `media-store`). SHOULD / MAY guidance with a *why* per rule.

The component adds the infra module `:media` (`{{ROOT_PACKAGE}}.media`): `LocalMediaStore` (pending files + kept
albums + TTL sweep) over `filesDir/media`, `MediaDeletionScheduler`, `StaleMediaSweeper` started at process start by
an `androidx.startup` initializer, the `MediaClock` / `MediaDirectory` seams, and `:app`'s `MediaConfigModule`
(the TTL).

## When to use

- The app captures media (photos, short videos, signatures, scans) as **working material** that is uploaded and
  then no longer needed on the device — especially on **shared devices** or with sensitive content.
- Captures must survive process death between the shutter and the upload, but must not live forever.

## When not to

- The user's captures are *theirs* (a camera or gallery app): use `MediaStore` / the shared collections.
- Long-lived offline documents the user manages: that is a document store with its own lifecycle, not a scratch
  area with a TTL.
- Large media that must survive weeks of offline use: the TTL sweep would delete it; design an explicit sync queue.

---

## 1. Storage

**MEDIA-01** — Captures SHOULD live in a subdirectory of `filesDir` (`filesDir/media`), never in the cache dir and
never in shared/external storage.
*Why:* the system may evict the cache between capture and upload, silently losing the user's work; external storage
is indexed by the media scanner and shows other people's captures in the gallery. `filesDir` is private and never
scanned.

**MEDIA-02** — The **capture timestamp SHOULD be encoded in the file name** (`media_<capturedAtMillis>_<uuid>.<ext>`),
and a name the store cannot parse SHOULD fall back to `lastModified()`.
*Why:* the in-memory list of pending captures dies with the process; with the age in the name the sweep needs no
database. An unreadable name must not make a file immortal — the exact leak the sweep exists to close.

**MEDIA-03** — The TTL SHOULD be measured with the **wall clock** (`MediaClock`), not the kernel's monotonic
`TimeSource`.
*Why:* "older than N hours" is a calendar question that must stay true across a reboot; a since-boot clock resets.

**MEDIA-04** — Files SHOULD be excluded from Auto Backup and device-to-device transfer
(`<exclude domain="file" path="media/" />` in `backup_rules.xml` and in both sections of
`data_extraction_rules.xml`).
*Why:* `filesDir` is backed up by default; scratch captures in a cloud backup outlive every deletion rule here.

## 2. Lifecycle

**MEDIA-05** — Publishing SHOULD **keep** a local copy under the **server's id** (`keep(path, albumId, mediaId)`:
a move into `albums/<albumId>/`, capture time carried over), a failed keep SHOULD delete the source, and ids SHOULD be
sanitised to `[A-Za-z0-9_-]` before they become path segments.
*Why:* a later screen looks each server item up by id instead of guessing by position; a kept copy is a nicety, so a
failure must not leave an unowned file behind; sanitising keeps a caller-chosen id from writing outside the store.

**MEDIA-06** — Ordinary cleanup SHOULD happen where the files are used (discard → `delete`, finished work →
`deleteAlbum`), and deletions requested by a screen that is about to close SHOULD go through
`MediaDeletionScheduler` (an app-lifetime scope).
*Why:* deletion is usually requested exactly when the ViewModel dies; its scope would cancel the delete half-way.

**MEDIA-07** — A **TTL sweep** SHOULD run once per process at startup, cover pending files and albums (removing
albums it empties), and never throw (failures are reported).
*Why:* it is the backstop for every run that reached no cleanup — a crash, a killed process, a device put down
mid-flow, work finished on another device. One pass per process suffices: nothing created after it can age past the
TTL before the next start.

**MEDIA-08** — Startup work of an optional module SHOULD be started from the module's own manifest
(`androidx.startup` `Initializer` merged into the app's `InitializationProvider`) and reach Hilt through an
`@EntryPoint`, rather than by editing the host's `App` class.
*Why:* the component installs and uninstalls without touching Core code (MOD-03), and the work also runs when the
process starts without UI. The trade-off: a host that removes `InitializationProvider` must call `start()` itself.

## 3. Configuration and tests

**MEDIA-09** — The TTL SHOULD be a host-provided `MediaStoreConfig(staleAfterMillis)` **data class** (`:app`'s
`MediaConfigModule`); the store module SHOULD NOT default it.
*Why:* the TTL is a product decision (longest legitimate capture-to-finish time plus margin) and a test build may
shrink it; a `@JvmInline value class` over a `Long` erases to a primitive Dagger cannot inject (DI-08).

**MEDIA-10** — The store SHOULD be a plain JVM class behind seams (`MediaDirectory`, `MediaClock`, an IO dispatcher)
and be tested against a **temporary folder** with a mutable fake clock: name round-trip, the TTL edge (exactly at
TTL is swept, one millisecond younger survives), unparseable names, missing directory, album round-trip, path
escapes.
*Why:* file-system rules are exactly what silently regresses; a temp dir makes them fast, real JVM tests.

## Adapting this to your app

- **Choose the TTL** in `app/di/MediaConfigModule.kt` from the longest legitimate flow (capture → publish → the work
  it documents is finished) plus margin. Shorter is safer on shared devices.
- **Add the backup exclusions** (MEDIA-04) by hand — the install doc shows the lines; the directory name is
  `MediaModule.MEDIA_DIR_NAME`.
- **Album ids** are whatever groups your kept files (an order, a ticket, a visit). The store never interprets them.
- **Camera integration** is not part of this component: the capture screen asks `createFile("jpg")` for a target
  file and writes into it (CameraX `OutputFileOptions(file)`), publishes, then `keep`s or `delete`s.
- **Example-only:** the `media_` prefix and the `albums/` layout — change them only together with the parser.
