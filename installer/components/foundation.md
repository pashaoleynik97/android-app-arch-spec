# Component: foundation (Core)

Rules: `specs/20-build-system.md` (BUILD-*) and `specs/toolchain.md` (known-good set + version-conditional
gotchas). Shorthands as in `INSTALL.md`: `SPEC=.arch-spec/spec`, `R` = `python3 $SPEC/installer/render.py --target .`

## What it adds

The build system every other component builds on. It replaces the stock Gradle files of the empty project; it
does **not** touch any Kotlin source, so the stock `MainActivity` and `ui/theme` keep compiling until
`architecture` replaces them. If `APP_PACKAGE` differs from the stock namespace, the foundation-only commit
**compiles but would not launch**: the manifest's `.MainActivity` resolves against the new namespace. That is
expected, because `architecture` follows in the same install. Don't hand out an APK from that commit.

| File | Purpose |
|---|---|
| `settings.gradle.kts` | `includeBuild("build-logic")`, repositories (`FAIL_ON_PROJECT_REPOS`), foojay toolchain resolver, `rootProject.name = "{{PROJECT_SLUG}}"`, the `// <arch-spec:includes>` marker block (holds `include(":app")`) |
| `build.gradle.kts` | every plugin `apply false` (shared classpath for the convention plugins) |
| `gradle.properties` | configuration cache ON, `android.disallowKotlinSourceSets=false` (KSP under built-in Kotlin) |
| `gradle/libs.versions.toml` | the known-good catalog: Core libraries + test kit + convention-plugin aliases; Recommended components append their own blocks. **No** `kotlin-android` alias |
| `gradle/gradle-daemon-jvm.properties`, `gradle/wrapper/gradle-wrapper.properties` | pinned daemon JVM and Gradle version of the known-good set |
| `build-logic/` | composite build: `ProjectConfig` (SDK levels from `MIN_SDK` / `TARGET_SDK` / `COMPILE_SDK` / `JVM_TARGET`), `internal Project.libs`, convention plugins `{{CONVENTION_PREFIX}}.android.application`, `.application.compose`, `.library`, `.library.compose`, `.hilt`; the application/library conventions carry the unit-test defaults (`isReturnDefaultValues`, JUnit4, coroutines-test, Turbine, AndroidJUnitRunner) |
| `app/build.gradle.kts` | conventions applied; versioning from `version.properties`; signing chain; `optimization { enable = false }` with the reason; the `// <arch-spec:app-dependencies>` marker block |
| `app/src/main/keepRules/rules.keep` | the keep-rule location for when R8 is enabled |
| `version.properties` | `VERSION_NAME=0.1.0` — single source of `versionName`/`versionCode` |
| `keystore.properties.template` | how to sign locally; the real `keystore.properties` and `{{KEYSTORE_FILE}}` are gitignored |
| `.gitignore` | stock entries + keystore/credentials; `.arch-spec/` is deliberately **not** ignored |

It **removes** the stock `app/proguard-rules.pro` (keep rules now live in `app/src/main/keepRules/`). If the user
had put rules in it, move them into `rules.keep` before deleting.

Versioning and signing behaviour of `app/build.gradle.kts`:
- `versionName` = `VERSION_NAME`, plus `-test` unless the build passes `-PreleaseBuild=true`;
- `versionCode` = `MMmmmPPP0` derived from `VERSION_NAME` (e.g. `1.2.95` → `1020950`), or `-PversionCodeOverride=<int>`
  when a pipeline supplies one;
- signing values come from `keystore.properties`, then env vars `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` /
  `KEY_PASSWORD`; with all four present debug **and** release use that key, otherwise release falls back to the
  debug key (keyless builds still succeed).

## Prerequisites

- A stock Android Studio "Empty Activity" (Compose, Kotlin DSL, version catalog) project, `R init` done.
- JDK 17+ to launch Gradle (the daemon itself runs on the JVM pinned in `gradle-daemon-jvm.properties`;
  Gradle provisions it through foojay on first use, which needs network once).
- Android SDK platform `COMPILE_SDK` installed (`sdkmanager "platforms;android-<COMPILE_SDK>"`).

## Questions (all skippable)

| Parameter | Ask | Safe placeholder / derivation |
|---|---|---|
| `PROJECT_SLUG` | "Short kebab-case project id?" | `APP_NAME` in kebab-case |
| `APPLICATION_ID` | "Application id for the store?" | from the stock `applicationId` |
| `APP_PACKAGE` | "Kotlin namespace of `:app`?" | = `APPLICATION_ID` |
| `ROOT_PACKAGE` | "Package root for the modules?" | `APP_PACKAGE` minus its last segment |
| `CONVENTION_PREFIX` | rarely asked | last segment of `ROOT_PACKAGE` |
| `MIN_SDK` | "Lowest Android version (API level)?" | stock `minSdk`, else 24 |
| `TARGET_SDK`, `COMPILE_SDK`, `JVM_TARGET` | not asked — known-good set | `specs/toolchain.md` |
| `KEYSTORE_FILE` | "Release keystore file name at the repo root?" | `{{PROJECT_SLUG}}-release.jks` (deferred `ci.signing`) |

Record with `R set KEY=VALUE …` (`KEY=-` = skipped).

## Install

```
R plan foundation       # expect: the stock Gradle files listed as replaced, app/proguard-rules.pro removed
R install foundation
```

No manual edits. Review the diff of `app/build.gradle.kts` against the stock one with the user only if they had
customised it (not the case in a fresh project).

## Gate

```
./gradlew help
./gradlew :app:assembleDebug
```

Show the command and the tail (`BUILD SUCCESSFUL`). The stock app still builds at this point.

## Completing deferred items

- **`ci.signing`** (`KEYSTORE_FILE`): `R set KEYSTORE_FILE=<name>.jks`, `R rerender KEYSTORE_FILE` (updates
  `.gitignore`, the template and `app/build.gradle.kts` if unmodified). The user then places the keystore at the
  repo root and creates `keystore.properties` from the template — never commit either, never generate a keystore
  for them.
- **Enabling R8** is not a deferred item but a recorded decision (BUILD-22): `optimization { enable = true }` in
  `app/build.gradle.kts`, `android.r8.gradual.support=true` in `gradle.properties` (the known-good AGP rejects
  `enable = true` without it), keep rules in `app/src/main/keepRules/rules.keep`, then `./gradlew :app:assembleRelease`
  and a smoke test of the release APK on a device.

## Troubleshooting

| Symptom | Cause → fix |
|---|---|
| `Plugin [id: 'org.jetbrains.kotlin.android'] …` / "Kotlin plugin is not needed" | AGP ≥ 9 has built-in Kotlin. Remove any `kotlin-android` alias or `kotlin("android")` (BUILD-12, BUILD-30). |
| KSP: "kotlin.sourceSets DSL is not allowed" | `android.disallowKotlinSourceSets=false` missing from `gradle.properties`. |
| `libs.androidx.*` unresolved in a module build file | something declared a public `Project.libs`; it must stay `internal` in build-logic. |
| `CommonExtension` type-argument / `defaultConfig { }` errors in build-logic | AGP ≥ 9 `CommonExtension` has no type parameters; use property access (`defaultConfig.apply { }`). |
| Hilt: "BaseExtension not found" | Hilt older than the catalog's; keep the known-good Hilt. |
| `compileSdk` error from an androidx artifact ("requires compileSdk N") | raise `COMPILE_SDK` (`R set COMPILE_SDK=N`, `R rerender COMPILE_SDK`), keep `TARGET_SDK`. |
| "Cannot find a Java installation … matching languageVersion=21" offline | the daemon JVM is provisioned through foojay; run once online or install that JDK locally. |
| Configuration-cache problem mentioning a file read | a build script read a file with `java.io`; use `providers.fileContents(...)`. |
| Platform `android-<COMPILE_SDK>` missing | install it with `sdkmanager`, or (user's choice, recorded) set `COMPILE_SDK` to the newest installed platform ≥ what the catalog's libraries require. |

## Commit message

`chore(arch-spec): foundation [spec vX.Y.Z]`
