# Component: images (Recommended)

Rules: `specs/recommended/images.md` (IMG-01 … IMG-09); toolchain pin `specs/toolchain.md` (BUILD-39). Shorthands as
in `INSTALL.md`: `SPEC=.arch-spec/spec`, `R` = `python3 $SPEC/installer/render.py --target .`

## What it adds

| Module / file | Purpose |
|---|---|
| `:files` (`{{ROOT_PACKAGE}}.files`) | `FilesConfig`, `FileUrlResolver`, `FilesAuthorizer` (bearer / cookie), `@FilesClient` OkHttp client (`FilesModule`, `@Multibinds` authorizers, debug logging via `:network`) |
| `files/src/withAuth/…/SessionFilesAuthModule.kt` | bearer token from `AuthManager` — compiled only when `:auth` exists |
| `:images` (`{{ROOT_PACKAGE}}.images`) | Coil `ImageLoader` over `@FilesClient` (`ImagesModule`), `ImageLoaderInitializer` (androidx.startup → `SingletonImageLoader.setSafe`), `{{THEME_PREFIX}}RemoteImage`, `{{THEME_PREFIX}}Avatar`, `Initials` |
| `app/…/di/FilesConfigModule.kt` | the host's `FilesConfig`, mapped from `NetworkConfig` — a new file, no Core file edited |
| tests | resolver, client (MockWebServer), initials |
| skill refs | `application-architecture/references/images.md`, `compose-ui/references/images-ui.md` |

Marker edits: `include(":files")`, `include(":images")`, two `:app` dependency lines, skill index lines,
`CLAUDE.md` `components` + `modules`. Catalog entries
used: `coil`, `coil-compose`, `coil-network-okhttp`, `androidx-startup-runtime`. They already ship in the foundation
catalog, so nothing is added.

## Prerequisites

- `network` installed (provides `NetworkConfig`, the OkHttp catalog entries, and the debug HTTP logging helper).
- Optional: `auth-session` (before or after) — files requests then carry the session's bearer token automatically.

## Questions (all skippable)

| Ask | Default |
|---|---|
| "Do server images need the user's credentials, and how — `Authorization: Bearer` or a cookie (name?)" | bearer when `auth-session` is installed; otherwise none |
| "Are files served from the API host under `v1/files/`, or from another host/path?" | API host + `v1/files/` (example contract) |

Answers other than the defaults are applied by hand after install (see *Install*). Nothing is a renderer parameter.

## Install

```
R plan images
R install images
```

Manual follow-ups only when the answers differ from the defaults:
- **Cookie auth:** in `files/src/withAuth/…/SessionFilesAuthModule.kt` return
  `FilesAuthorizer.cookie("<name>") { authManager.currentAccessToken() }`.
- **Other files host / path:** edit `app/…/di/FilesConfigModule.kt` (`FilesConfig(baseUrl = …, filesPath = …)`).
- **No credentials although a session exists** (public CDN): delete `files/src/withAuth/`.

## Gate

```
./gradlew assembleDebug
./gradlew :files:testDebugUnitTest :images:testDebugUnitTest
```

Optional: the merged manifest (`./gradlew :app:processDebugMainManifest`) lists
`{{ROOT_PACKAGE}}.images.ImageLoaderInitializer` under `androidx.startup.InitializationProvider`.

## Completing deferred items

- **`network.baseUrl`** (owned by `network`): files follow `NetworkConfig` automatically; nothing to re-render here.

## Troubleshooting

| Symptom | Fix |
|---|---|
| "incompatible metadata version" / "compiled with an incompatible version of Kotlin" in modules that see Coil | Coil was bumped past the Kotlin line — revert the `coil` version (BUILD-39) or bump `kotlin` with it |
| Server images always show the placeholder | the files host rejects the request: check logcat `OkHttp-Files` (debug) for 401/403 and the auth header; wrong scheme → switch bearer ↔ cookie |
| `more than one FilesAuthorizer is bound` at startup | two modules contribute an authorizer — keep one |
| `AsyncImage` requests carry no credentials | something created Coil's singleton before the initializer (e.g. the app removed `InitializationProvider`) — call `SingletonImageLoader.setSafe` from `App.onCreate` |
| `MissingBinding: FilesConfig` | `app/…/di/FilesConfigModule.kt` missing |

## Commit message

`chore(arch-spec): images [spec vX.Y.Z]`
