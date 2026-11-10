# 20 — Build system: build-logic, catalog, versioning, signing (BUILD)

Tier: **Core** (component `foundation`). Version numbers and version-conditional gotchas live in
`toolchain.md` (rules BUILD-30 and up). Placeholders: `{{CONVENTION_PREFIX}}`, `{{CONVENTION_PREFIX_PASCAL}}`,
`{{COMPILE_SDK}}`, `{{TARGET_SDK}}`, `{{MIN_SDK}}`, `{{JVM_TARGET}}`, `{{KEYSTORE_FILE}}`.

Contents: 1. Layout · 2. Convention plugins · 3. Catalog · 4. Root files · 5. Test defaults · 6. Versioning ·
7. Signing · 8. Release optimization

---

## 1. Layout

```
settings.gradle.kts            pluginManagement { includeBuild("build-logic") }, repositories, includes
build.gradle.kts               every plugin alias declared `apply false`, nothing else
gradle.properties              config cache, KSP flag, JVM args
gradle/libs.versions.toml      THE only place versions live
version.properties             VERSION_NAME=MAJOR.MINOR.PATCH (CI-owned)
keystore.properties.template   committed; the real keystore.properties is gitignored
build-logic/
  settings.gradle.kts          shares ../gradle/libs.versions.toml as `libs`
  convention/build.gradle.kts  `kotlin-dsl`; tooling deps compileOnly; gradlePlugin { plugins { register… } }
  convention/src/main/kotlin/  ProjectConfig.kt, KotlinAndroid.kt, AndroidCompose.kt, *ConventionPlugin.kt
```

**BUILD-01** — Shared build configuration MUST live in a `build-logic` **included build** (composite build,
`pluginManagement { includeBuild("build-logic") }`) that contributes convention plugins by id. There are no
shared `apply(from = …)` scripts, no `buildSrc`, no `subprojects {}` / `allprojects {}` blocks
(see ADR-0010).
*Why:* convention plugins are typed, compiled, cached and config-cache friendly; script plugins and
cross-project configuration are none of these and hide where a setting comes from.

**BUILD-02** — `build-logic/settings.gradle.kts` MUST import the root catalog
(`versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }`) and
`build-logic/convention/build.gradle.kts` MUST declare the Android/Kotlin/Compose/KSP/Hilt Gradle plugins as
`compileOnly` catalog dependencies.
*Why:* one catalog for both builds means one version per tool; `compileOnly` lets the consuming build supply
the real plugin versions so the two never disagree at runtime.

## 2. Convention plugins

**BUILD-03** — The build ships exactly these conventions (add more only through BUILD-09):

| Plugin id | Applies | Configures |
|---|---|---|
| `{{CONVENTION_PREFIX}}.android.application` | `com.android.application` | `configureKotlinAndroid`, `targetSdk`, test runner, test defaults (BUILD-14) |
| `{{CONVENTION_PREFIX}}.android.application.compose` | Compose compiler plugin | `configureAndroidCompose(ApplicationExtension)` |
| `{{CONVENTION_PREFIX}}.android.library` | `com.android.library` | `configureKotlinAndroid`, test runner, test defaults (BUILD-14) |
| `{{CONVENTION_PREFIX}}.android.library.compose` | Compose compiler plugin | `configureAndroidCompose(LibraryExtension)` |
| `{{CONVENTION_PREFIX}}.android.hilt` | KSP + Hilt Gradle plugin | `implementation(hilt-android)`, `ksp(hilt-compiler)`, `compileOnly(errorprone-annotations)` |

`configureKotlinAndroid` sets `compileSdk = {{COMPILE_SDK}}`, `minSdk = {{MIN_SDK}}`, Java source/target
`{{JVM_TARGET}}`. `configureAndroidCompose` sets `buildFeatures.compose = true` and adds the Compose BOM
(`implementation` + `androidTestImplementation`), `ui`, `ui-graphics`, `ui-tooling-preview`, `material3`, and
`debugImplementation(ui-tooling)`.
*Why:* `:app` is a Compose module too; without an application-compose convention it re-declares the BOM and
Compose deps by hand and drifts from the libraries.

**BUILD-04** — A module build file is declarative: `plugins { alias(libs.plugins.{{CONVENTION_PREFIX}}.android.…) }`,
`android { namespace = "…" }`, and `dependencies {}`. It MUST NOT set anything a convention owns (`compileSdk`,
`minSdk`, Java version, `buildFeatures.compose`, the Compose BOM, Hilt deps, test defaults).
```kotlin
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library.compose)   // after .library
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.hilt)
}
android { namespace = "{{ROOT_PACKAGE}}.orders.ui" }
dependencies { implementation(project(":lib-android")) /* … */ }
```
*Why:* duplicated settings diverge silently (one module on a different Java target is a classic runtime
`NoSuchMethodError`).

**BUILD-05** — The `.compose` conventions configure the extension created by the base convention, so they MUST be
listed **after** `.android.library` / `.android.application` in `plugins {}`; a `.compose` plugin applied alone
fails fast with a clear message.
*Why:* the extension lookup by type fails with an obscure error if the base plugin is missing.

**BUILD-06** — SDK levels live in one `object ProjectConfig` in `build-logic` (`COMPILE_SDK`, `TARGET_SDK`,
`MIN_SDK`), each with a comment saying what forces its value.
*Why:* `compileSdk` is often forced by a library (see `toolchain.md`); the comment stops a "cleanup" from
lowering it.

**BUILD-07** — Inside convention code the catalog is reached through an **`internal`**
`val Project.libs: VersionCatalog` extension with string lookups (`libs.findLibrary("androidx-compose-bom").get()`).
*Why:* a public extension leaks onto every module's build-script classpath and shadows Gradle's generated
type-safe `libs` accessor (breaks `libs.androidx.*` everywhere).

**BUILD-08** — Convention classes live in the default package of `build-logic/convention`, are named
`Android<Thing>ConventionPlugin`, and share helpers as `internal fun Project.configureX(…)`.
*Why:* a predictable shape lets an agent add a convention by analogy.

**BUILD-09** — Adding a convention: add the class, register it in `gradlePlugin { plugins { register(…) } }`
with id `{{CONVENTION_PREFIX}}.android.<name>`, add a catalog plugin marker
`{{CONVENTION_PREFIX}}-android-<name> = { id = "{{CONVENTION_PREFIX}}.android.<name>", version = "unspecified" }`,
then update this table and the architecture skill in the same change.
*Why:* the `unspecified` marker is how a module applies an included-build plugin through `libs.plugins.*`.

## 3. Version catalog

**BUILD-10** — Every external version MUST be declared in `gradle/libs.versions.toml` and referenced as
`libs.*`; no inline version anywhere in a build file. The one tolerated exception is a plugin in the
`settings.gradle.kts` `plugins {}` block (e.g. the JDK toolchain resolver), where the catalog is not yet
available — it carries a comment saying so.
*Why:* one place to upgrade, one place to audit; inline versions are how two copies of a library end up on the
classpath.

**BUILD-11** — `[libraries]` are grouped under comment headers by concern (build-logic classpath, DI and
coroutines, Compose, network, test-only, and one header per Recommended component that adds entries). A version
that is pinned below latest for a reason carries that reason as a comment **directly above** it, including what
must change before it can move.
*Why:* a pin without a reason gets "upgraded" by the next agent and breaks the build in a way that takes hours
to trace back.

**BUILD-12** — The catalog MUST NOT declare plugins that must never be applied (notably the standalone Kotlin
Android plugin on a toolchain with built-in Kotlin, BUILD-30) and SHOULD NOT keep unused entries.
*Why:* a declared alias is an invitation; unused entries rot and mislead upgrades.

## 4. Root files

**BUILD-13** — Root files:
- `settings.gradle.kts`: `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)`; `google()` restricted
  with `content { includeGroupByRegex(…) }` to Android/Google/AndroidX groups in `pluginManagement`.
- root `build.gradle.kts`: **every** plugin alias any module applies is declared `apply false` (Android
  application/library, Compose compiler, Kotlin serialization, KSP, Hilt), with a comment that this puts them on
  the shared classpath for the conventions.
- `gradle.properties`: `org.gradle.configuration-cache=true`, `kotlin.code.style=official`, JVM args, and the
  KSP compatibility flag required by the toolchain (BUILD-32), each with a comment saying why.
- `.gitignore`: standard Android entries plus `keystore.properties`, `*.jks`, `*.keystore`; every module and
  `build-logic` has its own `.gitignore` with `/build`.
*Why:* modules declaring repositories make resolution order non-deterministic; a plugin applied by alias but not
declared at the root loads in a separate classloader and can break type sharing between plugins.

**BUILD-14** — The configuration cache MUST stay on, so build scripts and conventions MUST read files only through
`providers` (`providers.fileContents(layout.projectDirectory.file("…")).asText`) and Gradle properties through
`providers.gradleProperty(…)`; never `File(...).readText()` at configuration time.
*Why:* a direct read is not tracked — a CI job that rewrites `version.properties` and then builds would reuse a
cached configuration and bake the old version into the artifact.

## 5. Test defaults in conventions

**BUILD-15** — The library and application conventions MUST own unit-test defaults:
`testOptions.unitTests.isReturnDefaultValues = true`,
`testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`, and the baseline test dependencies
(`testImplementation` of JUnit and kotlinx-coroutines-test; the shared `:core-testing` module where it exists,
see `70-testing.md`).
*Why:* `Reporter` calls `android.util.Log`; on the JVM the stubbed `android.jar` throws "not mocked" unless
defaults are returned. Repeating the flag in every module is how one module forgets it.

**BUILD-16** — Kotlin compiler options, when needed, are set only with the `kotlin { compilerOptions { } }` DSL
inside a convention, never per module.
*Why:* the legacy `kotlinOptions {}` DSL is gone under built-in Kotlin (BUILD-30), and per-module flags diverge.

## 6. Versioning

**BUILD-17** — `version.properties` at the repo root holds exactly one line `VERSION_NAME=MAJOR.MINOR.PATCH`
(three integers). It is the single source of the version. With a CI bot it is owned by CI: a release job bumps
and commits it. Without one (`ci-cd` bot mode `none`) it changes only through a reviewed PR, and `versionCode`
comes from `-PversionCodeOverride` (BUILD-18). `app/build.gradle.kts` reads it through `providers.fileContents` (BUILD-14) and fails the build with a
clear message if the line is missing or malformed.
*Why:* a version typed into a build file is bumped by hand and forgotten; a CI-owned file makes the released
version reproducible from the tag.

**BUILD-18** — `versionCode` is **derived**, never stored:
```kotlin
// capacities: major, minor <= 99; patch <= 999; the trailing digit is reserved (hotfix rebuilds)
val computedVersionCode = (major * 100_000 + minor * 1_000 + patch) * 10
```
An explicit `-PversionCodeOverride=<int>` wins when present. CI passes the commit count there when no bot
pushes version bumps (`ci-cd` bot mode `none`), so every merge build still gets a higher code.
*Why:* a derived code is monotonic whenever the version name grows, so the store never rejects an upload for
a stale code, and nobody has to remember to bump two numbers.

**BUILD-19** — Every build that is not the release pipeline gets a `-test` suffix on `versionName`; the release
pipeline passes `-PreleaseBuild=true` to drop it.
```kotlin
val isReleaseBuild = providers.gradleProperty("releaseBuild").orNull == "true"
versionName = if (isReleaseBuild) versionNameBase else "$versionNameBase-test"
```
*Why:* a tester can tell from the About screen whether an APK came from the release pipeline.

## 7. Signing

**BUILD-20** — Signing values resolve per key in this order: local `keystore.properties` (gitignored, parsed via
`providers.fileContents`, `#` and blank lines skipped) → environment variable.

| Property | Env var |
|---|---|
| `storeFile` (default `{{KEYSTORE_FILE}}` at the repo root) | `KEYSTORE_FILE` |
| `keystorePassword` | `KEYSTORE_PASSWORD` |
| `keyAlias` | `KEY_ALIAS` |
| `keyPassword` | `KEY_PASSWORD` |

A committed `keystore.properties.template` documents the keys.
*Why:* developers sign locally from a file, CI from secrets, and the build script has one code path for both.

**BUILD-21** — When all four values are present, **both** `debug` and `release` are signed with that key; when
any is missing, `release` falls back to the debug key so keyless builds (fresh clone, PR CI) still succeed. A
keystore or `keystore.properties` MUST NEVER be committed; the installer never creates keys.
*Why:* identical signatures let an IDE-installed build update over a CI artifact without uninstalling (and losing
data); the keyless fallback keeps the build green before signing is set up (a deferred `ci.signing` item).

## 8. Release optimization (R8)

**BUILD-22** — Whether release builds run R8 (shrinking/obfuscation/optimization) MUST be an explicit,
recorded decision (an ADR in the target project), not a template default nobody looked at. The shipped default
is **disabled**, with the reason in a comment next to the setting:
- enabling requires keep rules for reflection-based code (kotlinx-serialization serializers, Retrofit service
  interfaces, Hilt-generated entry points are covered by their libraries; project DTOs and anything looked up
  by name are not), placed in the keep-rule location the AGP version expects (`toolchain.md`);
- enabling requires a release smoke test on a device before the first release, because R8 failures surface at
  runtime, not at build time.
*Why:* R8 silently breaks reflection at runtime; leaving it "whatever the template had" means either an
unshrunk APK nobody chose or a crash nobody tested.

**BUILD-23** — Product flavors are not used by default. Per-environment values (base URLs) are injected by the
host from a single DI module (MOD-15); a project that adds flavors moves those values to the flavor and keeps
the single injection point.
*Why:* flavors multiply every Gradle task and CI cost; one injection point keeps the switch to flavors a local
change.
