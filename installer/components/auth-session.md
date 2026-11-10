# Component: auth-session (Recommended)

Rules: `specs/recommended/auth-session.md` (AUTH-01 … AUTH-16); ADR-0008, ADR-0009.

## What it adds

| File | Purpose |
|---|---|
| `auth/…` (module `:auth`) | `AuthManager` + `DefaultAuthManager` (single-flight refresh, generation guard, keep-alive), classifier, `@AuthClient`, Keystore token store, profile store, activity tracker, `SessionShell` / `SessionPolicy`; contributes the bearer interceptor and the token authenticator to `:network`'s seams |
| `auth/src/test/…`, `auth/src/androidTest/…` | `AuthTestEnv` + fakes, single-flight / zombie / keep-alive / classification / integration tests; Keystore round trip (instrumented) |
| `login-ui-api/`, `login-ui/`, `login/` | a minimal sign-in feature (email + password) with tests |
| `app/src/main/java/<app package>/session/SessionHostExtension.kt`, `di/SessionHostModule.kt` | the session's host layer: navigation follows the session, the session notice, activity stamping |
| `app/src/main/res/values/session_strings.xml` | notice texts |
| `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml` | session-file exclusions inserted into Core's backup marker blocks (no file replaced) |
| `.mitm_scripts/v1_auth_refresh/POST_401_session-revoked.{py,md}` | device scenario (used with `mitm-testing`) |
| `.claude/skills/application-architecture/references/auth-session.md` | skill reference |

Marker inserts: settings (`:auth`, `:login-ui-api`, `:login-ui`, `:login`), app dependencies, `App.kt`
(`LoginFeatureImpl().addToRegistry()`), the backup-rule marker blocks, the skill index, `CLAUDE.md`.

## Prerequisites

`network` installed (it supplies `NetworkConfig`, the `Json`, the client seams).

## Questions

None. Tell the user: the auth endpoints and DTOs are an **example contract** (`auth/…/network/AuthApi.kt`,
`dto/AuthDtos.kt`, `AuthErrorCode.kt`) matching the mitm-testing fake backend; adapt them in code.

## Install

```
R plan auth-session
R install auth-session
```

The session files' Auto Backup exclusions are inserted into the marker blocks of Core's
`app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`. Check that the lines are there. If someone
removed the markers by hand, the insert fails with "marker block not found": restore the markers and re-run.

## Gate

```
./gradlew assembleDebug
./gradlew :auth:testDebugUnitTest :login:testDebugUnitTest :login-ui:testDebugUnitTest
```

Optional, on an emulator: `./gradlew :auth:connectedDebugAndroidTest` (real Keystore crypto).

## Completing deferred items

None of its own. With `network.baseUrl` deferred the app signs in against a placeholder host — every sign-in
answers "No connection to the server" until the base URL is set (see `installer/components/network.md`).

## Troubleshooting

| Symptom | Fix |
|---|---|
| Hilt: `HostExtension` set missing | Core's `HostExtensionsModule` (`@Multibinds`) was removed from `:app` — restore it |
| App starts on the home screen, then jumps to sign-in | expected while the session resolves (`Initializing`); add a splash if it bothers (spec: "Adapting") |
| Sign-in succeeds but the screen stays | the start destination route is missing from the graph — check `AppNavHost`'s `startDestination` |
| Every request 401s right after sign-in | the backend expects another header scheme — adapt `AuthInterceptor` |
| Manifest merger error on `dataExtractionRules` / `fullBackupContent` | the app manifest must reference `@xml/data_extraction_rules` and `@xml/backup_rules` (Core's manifest does) |

## Commit message

`chore(arch-spec): auth-session [spec vX.Y.Z]`
