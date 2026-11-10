# Component: connectivity (Recommended)

Rules: `specs/recommended/connectivity.md` (CONN-01 … CONN-08).

## What it adds

| File | Purpose |
|---|---|
| `connectivity/build.gradle.kts` | the module; `BASE_URL` per build type + `HEALTH_PATH` (the probe's environment) |
| `connectivity/src/main/AndroidManifest.xml` | `ACCESS_NETWORK_STATE`, `INTERNET` |
| `…/connectivity/{NetworkState,NetworkStateWatch}.kt`, `config/`, `di/`, `internal/` | the watch, OS monitor, health probe on `@ConnectivityClient`, pure resolver |
| `…/connectivity/banner/*` | pure banner policy + the banner controller |
| `connectivity/src/test/…` | resolver table, watch (virtual time), probe (MockWebServer), banner tests |
| `app/src/main/java/<app package>/connectivity/ConnectivityBannerExtension.kt`, `di/ConnectivityHostModule.kt`, `res/values/connectivity_strings.xml` | the banner layer of the host |
| `.mitm_scripts/<health-path slug>/GET_503_server-unreachable.{py,md}` | device scenario (used with `mitm-testing`) |
| `.claude/skills/application-architecture/references/connectivity.md` | skill reference |

Independent of `network`: it builds on Core alone.

## Prerequisites

`architecture` installed. A health endpoint on the backend (unauthenticated, cheap, 2xx when healthy).

## Questions (all skippable)

| Parameter | Ask | Safe placeholder |
|---|---|---|
| `HEALTH_PATH` | "Health-check path, relative to the base URL (no leading slash)?" | `health` (deferred `connectivity.healthPath`) |
| `BASE_URL_DEBUG` / `BASE_URL_RELEASE` | usually already answered for `network` | `https://api.example.invalid/` |

## Install

```
R plan connectivity
R install connectivity
```

## Gate

```
./gradlew assembleDebug
./gradlew :connectivity:testDebugUnitTest
```

## Completing deferred items

- **`connectivity.healthPath`** / **`network.baseUrl`**: `R set HEALTH_PATH=… BASE_URL_DEBUG=…`, then
  `R rerender HEALTH_PATH BASE_URL_DEBUG BASE_URL_RELEASE` (re-renders `connectivity/build.gradle.kts` and the
  scenario script if unmodified). The scenario folder is named after the health path (`v1/health` → `.mitm_scripts/v1_health/`). After
  `R rerender HEALTH_PATH` it is rendered under the new folder; delete the old one.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Banner "No connection to the server" on a working network | the health path / base URL is a placeholder or wrong — the probe needs a real 2xx endpoint |
| Banner never appears in airplane mode | the app lacks `ACCESS_NETWORK_STATE` in the merged manifest — check manifest merging |
| `ConnectivityConfig.baseUrl must end with '/'` | fix the base URL answer |

## Commit message

`chore(arch-spec): connectivity [spec vX.Y.Z]`
