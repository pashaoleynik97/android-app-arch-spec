# Component: network (Recommended)

Rules: `specs/recommended/network.md` (NET-01 … NET-12), on top of Core DATA-10 … DATA-24.

## What it adds

| File | Purpose |
|---|---|
| `network/build.gradle.kts` | the module; `BASE_URL` per build type (the single place the environment is set) |
| `network/src/main/AndroidManifest.xml` | `INTERNET` permission |
| `…/network/config/NetworkConfig.kt`, `di/NetworkConfigModule.kt` | the no-default config and its one provider |
| `…/network/di/NetworkModule.kt`, `di/Qualifiers.kt` | `@ApiClient` OkHttp, `@ApiRetrofit`, the shared `Json`, the `@Multibinds` seams |
| `…/network/ApiInterceptor.kt` | the interceptor seam optional modules contribute to |
| `…/network/{ApiCall,ApiError,HttpStatus,RefreshPolicy}.kt`, `dto/ApiEnvelope.kt` | classifier skeleton, error helpers, request policy tag, example envelope |
| `…/network/log/*` | Reporter-routed HTTP logging: masking, byte-budget chunker, persisted policy |
| `network/src/test/…` | MockWebServer + pure tests (client seams, classifier, helpers, logging) |
| `.claude/skills/application-architecture/references/network.md` | skill reference |

Marker inserts: `settings.gradle.kts` (`:network`), `app/build.gradle.kts`, the application-architecture
`SKILL.md` references index, `CLAUDE.md` components + modules. Catalog entries (Retrofit, OkHttp BOM,
kotlinx-serialization, MockWebServer) are already in Core's `gradle/libs.versions.toml`.

## Prerequisites

`architecture` installed.

## Questions (all skippable)

| Parameter | Ask | Safe placeholder |
|---|---|---|
| `BASE_URL_DEBUG` | "API base URL for debug builds (ends with `/`)?" | `https://api.example.invalid/` (deferred `network.baseUrl`) |
| `BASE_URL_RELEASE` | "…and for release builds?" (default: same as debug) | = `BASE_URL_DEBUG` |

Also tell the user the envelope / error shape is an **example contract** they adapt in code
(`dto/ApiEnvelope.kt`) — it is not asked.

## Install

```
R plan network
R install network
```

No manual steps.

## Gate

```
./gradlew assembleDebug
./gradlew :network:testDebugUnitTest
```

## Completing deferred items

- **`network.baseUrl`**: `R set BASE_URL_DEBUG=https://…/ BASE_URL_RELEASE=https://…/`, then
  `R rerender BASE_URL_DEBUG BASE_URL_RELEASE` (re-renders `network/build.gradle.kts` and, if installed, the
  connectivity build file and the mitm harness — each only if unmodified; otherwise edit the `buildConfigField`
  lines by hand). Re-run the gate.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `NetworkConfig.baseUrl must end with '/'` at startup | the base URL answer lacks the trailing slash — `R set` validates it; a hand edit does not |
| Hilt: `Json` / `Retrofit` bound multiple times | another module provides an unqualified one — reuse this module's `Json`; qualify any other `Retrofit` |
| `more than one @ApiClient Authenticator is bound` | two modules contribute an authenticator — keep one that delegates |
| `NetworkConfig` bound multiple times | it was moved to `:app` without deleting `NetworkConfigModule` |
| Unit test: `Method … not mocked` | the module lost the library convention's test defaults — apply `{{CONVENTION_PREFIX}}.android.library` |

## Commit message

`chore(arch-spec): network [spec vX.Y.Z]`
