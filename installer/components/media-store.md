# Component: media-store (Recommended)

Rules: `specs/recommended/media-store.md` (MEDIA-01 … MEDIA-10). Shorthands as in `INSTALL.md`:
`SPEC=.arch-spec/spec`, `R` = `python3 $SPEC/installer/render.py --target .`

## What it adds

| Module / file | Purpose |
|---|---|
| `:media` (`{{ROOT_PACKAGE}}.media`) | `LocalMediaStore` + `InternalMediaStore` (`filesDir/media`, pending + kept albums, TTL sweep), `MediaDeletionScheduler`, `StaleMediaSweeper`, `MediaStartupInitializer`, `MediaModule` (IO dispatcher, app scope, wall clock, directory) |
| `media/src/main/AndroidManifest.xml` | merges the initializer into `androidx.startup.InitializationProvider` |
| `media/src/test/…` | store tests against a temp folder, sweeper and scheduler tests |
| `app/…/di/MediaConfigModule.kt` | the host's `MediaStoreConfig` (TTL, default 12 h) — a new file, no Core file edited |
| `.claude/skills/application-architecture/references/media-store.md` | rules + usage |

Marker edits: `include(":media")`, `implementation(project(":media"))` in `:app`, skill index line, `CLAUDE.md`
`components` + `modules` lines. Catalog: `androidx-startup-runtime` (section `component: media-store`).

## Prerequisites

- `architecture` installed. No network needed.

## Questions (all skippable)

None at install. The TTL is set in code (see *Install*); the default (12 hours) is a safe placeholder for a
capture-then-upload flow within one working day.

## Install

```
R plan media-store
R install media-store
```

Manual steps (tell the user; do them only with consent):

1. Nothing to do for backups: the install inserts `<exclude domain="file" path="media/" />` into the
   backup marker blocks of Core's `backup_rules.xml` and `data_extraction_rules.xml` (MEDIA-04). Check that the line
   is there; a project that edited those files by hand may have lost the markers.
2. **Choose the TTL** in `app/src/main/java/…/di/MediaConfigModule.kt` (`STALE_AFTER_HOURS`): the longest legitimate
   capture → finish time plus margin.

## Gate

```
./gradlew assembleDebug
./gradlew :media:testDebugUnitTest
```

Optional: `./gradlew :app:processDebugMainManifest` and check the merged manifest's `InitializationProvider` lists
`{{ROOT_PACKAGE}}.media.MediaStartupInitializer`.

## Completing deferred items

Nothing is deferred.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `MissingBinding: MediaStoreConfig` | `app/…/di/MediaConfigModule.kt` was deleted or moved out of a Hilt module — restore it |
| Old captures are never swept | the merged manifest lost `InitializationProvider` (e.g. `tools:node="remove"` for WorkManager) — call `StaleMediaSweeper.start()` from `App.onCreate` (inject it there) |
| Captures appear in the gallery | something writes outside `LocalMediaStore` (`MediaStore` / external dirs) — route the capture through `createFile` |
| Manifest merger error about `androidx-startup` authority | another library declares the provider with a different authority — keep `${applicationId}.androidx-startup` and `tools:node="merge"` |

## Commit message

`chore(arch-spec): media-store [spec vX.Y.Z]`
