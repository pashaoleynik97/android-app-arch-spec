# Build system — build-logic, catalog, root files, versioning, signing (component: foundation)

Rules for every Gradle file. Spec: `.arch-spec/spec/specs/20-build-system.md`. Catalog-only versions
(BUILD-10) is a golden rule in `SKILL.md`; version-conditional gotchas are in the toolchain reference.

Contents: 1. Layout · 2. Convention plugins · 3. Catalog · 4. Root files · 5. Test defaults ·
6. Versioning · 7. Signing · 8. Release optimization, module graph check, lint and formatting · 9. Recipes

---

## 1. Layout

```
settings.gradle.kts            pluginManagement { includeBuild("build-logic") }, repositories, includes
build.gradle.kts               every plugin alias declared `apply false`, nothing else
gradle.properties              configuration cache, KSP flag, JVM args — each with a comment
gradle/libs.versions.toml      the only place versions live
version.properties             VERSION_NAME=MAJOR.MINOR.PATCH
keystore.properties.template   committed; the real keystore.properties is gitignored
build-logic/settings.gradle.kts            shares ../gradle/libs.versions.toml as `libs`
build-logic/convention/build.gradle.kts    kotlin-dsl; plugin APIs compileOnly; gradlePlugin { register … }
build-logic/convention/src/main/kotlin/    ProjectConfig.kt, KotlinAndroid.kt, AndroidCompose.kt, *ConventionPlugin.kt
```

**BUILD-01** — Shared build configuration lives only in the `build-logic` included build as convention
plugins applied by id; no `apply(from = …)` scripts, no `buildSrc`, no `subprojects {}` / `allprojects {}`.
*Why:* convention plugins are typed, compiled, cached and configuration-cache friendly; the alternatives hide
where a setting comes from.
<!-- origin: arch-spec BUILD-01 -->

**BUILD-02** — `build-logic/settings.gradle.kts` imports the root catalog as `libs`, and the convention module
declares the Android / Kotlin / Compose / KSP / Hilt Gradle plugins as `compileOnly` catalog dependencies.
*Why:* one catalog for both builds means one version per tool; `compileOnly` lets the root build supply the
real plugin versions so the two never disagree at runtime.
<!-- origin: arch-spec BUILD-02 -->

## 2. Convention plugins

| Plugin id | Class | Applies / configures |
|---|---|---|
| `{{CONVENTION_PREFIX}}.android.application` | AndroidApplicationConventionPlugin | `com.android.application`, SDK + Java levels, test defaults |
| `{{CONVENTION_PREFIX}}.android.application.compose` | AndroidApplicationComposeConventionPlugin | Compose compiler, BOM + core Compose deps |
| `{{CONVENTION_PREFIX}}.android.library` | AndroidLibraryConventionPlugin | `com.android.library`, SDK + Java levels, test defaults |
| `{{CONVENTION_PREFIX}}.android.library.compose` | AndroidLibraryComposeConventionPlugin | Compose compiler, BOM + core Compose deps |
| `{{CONVENTION_PREFIX}}.android.hilt` | AndroidHiltConventionPlugin | KSP + Hilt plugin, hilt-android, hilt-compiler, errorprone annotations |
| `{{CONVENTION_PREFIX}}.feature.api` | AndroidFeatureApiConventionPlugin | `.android.library` + `api(:feature-api)` — a feature contract `:x-ui-api` |
| `{{CONVENTION_PREFIX}}.feature.ui` | AndroidFeatureUiConventionPlugin | `.android.library` + `.library.compose` + `.hilt`, `:lib-android`, navigation-compose, hilt-lifecycle-viewmodel-compose, test `:core-testing` |
| `{{CONVENTION_PREFIX}}.feature.domain` | AndroidFeatureDomainConventionPlugin | `.android.library` + `.hilt`, `:lib-common`, javax.inject, test `:core-testing` |

Shared helpers live in `KotlinAndroid.kt` (SDK, Java, unit-test defaults) and `AndroidCompose.kt`.

**BUILD-03** — The build ships exactly the conventions in the table above; more are added only through the
recipe in section 9. Feature-trio modules apply their role convention (`feature.api` / `feature.ui` /
`feature.domain`) alone; other modules combine the platform ones. *Why:* the host is a Compose module too —
without an application-compose convention it re-declares the BOM and Compose deps by hand and drifts from the
libraries; without role conventions every feature copies the same plugin set and baseline edges, and a change to
them means editing every feature.
<!-- origin: arch-spec BUILD-03 -->

**BUILD-04** — A module build file is declarative: plugin aliases, `android { namespace = "…" }`,
`dependencies {}`. It never sets what a convention owns (SDK levels, Java target, Compose flags or BOM, Hilt
deps, test defaults). *Why:* duplicated settings diverge silently — one module on a different Java target is
a classic runtime NoSuchMethodError.
<!-- origin: arch-spec BUILD-04 -->

```kotlin
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.feature.ui)   // brings library + compose + hilt + :lib-android + nav
}
android { namespace = "{{ROOT_PACKAGE}}.orders.ui" }
dependencies { implementation(project(":orders-ui-api")); implementation(project(":orders")) }
```
A non-feature module lists platform conventions instead: `.android.library`, then `.android.library.compose`,
then `.android.hilt`.

**BUILD-05** — A `.compose` convention is listed after its base `.android.library` / `.android.application`
convention; applied alone it fails fast with a clear message. *Why:* it configures the extension the base
plugin creates; the lookup by type fails obscurely without it.
<!-- origin: arch-spec BUILD-05 -->

**BUILD-06** — SDK levels live in the one ProjectConfig object in `build-logic`, each with a comment saying
what forces its value. *Why:* the compile SDK is often forced by a library; the comment stops a "cleanup"
from lowering it.
<!-- origin: arch-spec BUILD-06 -->

**BUILD-07** — Convention code reaches the catalog through an `internal` `Project.libs` extension with string
lookups (`libs.findLibrary("junit").get()`). *Why:* a public extension leaks onto every module's build-script
classpath and shadows Gradle's generated type-safe `libs` accessor.
<!-- origin: arch-spec BUILD-07 -->

**BUILD-08** — Convention classes live in the default package of the convention module, are named
`Android<Thing>ConventionPlugin`, and share helpers as `internal fun Project.configureX(…)`.
*Why:* a predictable shape lets a new convention be added by analogy.
<!-- origin: arch-spec BUILD-08 -->

**BUILD-09** — Adding a convention means: the class, its `register(…)` entry with id
`{{CONVENTION_PREFIX}}.android.<name>`, a catalog plugin marker with `version = "unspecified"`, and an update
of the table above — in one change. *Why:* the `unspecified` marker is how a module applies an included-build
plugin through `libs.plugins.*`.
<!-- origin: arch-spec BUILD-09 -->

## 3. Catalog

**BUILD-11** — Catalog entries are grouped under comment headers by concern (build-logic classpath, DI,
coroutines, androidx, Compose, testing, then one header per Recommended component). A version pinned below
latest carries its reason directly above it, including what must change before it may move. *Why:* a pin
without a reason gets "upgraded" by the next agent and breaks the build in a way that takes hours to trace.
<!-- origin: arch-spec BUILD-11 -->

**BUILD-12** — The catalog never declares a plugin that must not be applied (the standalone Kotlin Android
plugin, BUILD-30) and keeps no unused entries. *Why:* a declared alias is an invitation; unused entries rot
and mislead upgrades.
<!-- origin: arch-spec BUILD-12 -->

## 4. Root files

**BUILD-13** — Root files: settings use `FAIL_ON_PROJECT_REPOS` and restrict `google()` to Android / Google /
AndroidX groups; the root build declares **every** plugin alias any module applies with `apply false`;
`gradle.properties` comments each flag; the root `.gitignore` excludes the keystore and its properties, and
every module (and `build-logic`) has its own `.gitignore` with `/build`. *Why:* modules declaring repositories
make resolution non-deterministic; a plugin not declared at the root loads in a separate classloader.
<!-- origin: arch-spec BUILD-13 -->

**BUILD-14** — The configuration cache stays on, so scripts and conventions read files only through
`providers.fileContents(…)` and properties through `providers.gradleProperty(…)` /
`providers.environmentVariable(…)` — never `File(…).readText()` or `System.getenv()` at configuration time.
*Why:* a direct read is not tracked; a pipeline that rewrites `version.properties` and then builds would reuse
a stale configuration.
<!-- origin: arch-spec BUILD-14 -->

## 5. Test defaults

**BUILD-15** — The library and application conventions own unit-test defaults:
`unitTests.isReturnDefaultValues = true`, the AndroidX test runner, and `testImplementation` of JUnit,
kotlinx-coroutines-test and Turbine (see `KotlinAndroid.kt`). Modules add only their own test deps (e.g.
`testImplementation(project(":core-testing"))`). *Why:* the Reporter calls `android.util.Log`, which throws
"not mocked" on the JVM stub unless defaults are returned; a per-module flag is forgotten in the next module.
<!-- origin: arch-spec BUILD-15 -->

**BUILD-16** — Kotlin compiler options are set only with `kotlin { compilerOptions { } }` inside a convention.
*Why:* the legacy `kotlinOptions {}` DSL is gone under built-in Kotlin, and per-module flags diverge.
<!-- origin: arch-spec BUILD-16 -->

## 6. Versioning

**BUILD-17** — `version.properties` holds exactly `VERSION_NAME=MAJOR.MINOR.PATCH`; it is the single source of
the version (bumped by the release pipeline, or by a pull request when no pipeline bumps it).
`app/build.gradle.kts` reads it through `providers` and fails with a clear message when it is malformed.
*Why:* a version typed into a build file is bumped by hand and forgotten.
<!-- origin: arch-spec BUILD-17 -->

**BUILD-18** — `versionCode` is derived, never stored: `(major * 100_000 + minor * 1_000 + patch) * 10`
(major, minor ≤ 99; patch ≤ 999; trailing digit reserved). *Why:* a derived code grows whenever the version
name grows, so the store never rejects an upload, and nobody bumps two numbers.
<!-- origin: arch-spec BUILD-18 -->

**BUILD-19** — Every build except the release pipeline's gets a `-test` suffix on `versionName`; the release
pipeline passes `-PreleaseBuild=true`. *Why:* a tester can tell a release APK from a test APK on the About
screen.
<!-- origin: arch-spec BUILD-19 -->

## 7. Signing

**BUILD-20** — Signing values resolve per key from the local gitignored `keystore.properties` first, then from
the environment (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`);
`keystore.properties.template` documents the keys. *Why:* developers sign from a file, CI from secrets, with
one code path for both.
<!-- origin: arch-spec BUILD-20 -->

**BUILD-21** — With all four values present both debug and release are signed with that key; with any
missing, release falls back to the debug key. A keystore or its properties file is never committed, and
agents never create keys. *Why:* identical signatures let an IDE build update over a CI artifact without
losing data; the fallback keeps keyless builds green until signing is set up (a deferred item).
<!-- origin: arch-spec BUILD-21 -->

## 8. Release optimization

**BUILD-22** — Turning R8 on for release is an explicit, recorded decision (an ADR), never a template default.
The shipped default is off, with the reason next to the setting in `app/build.gradle.kts`. Enabling it needs
keep rules for reflection-based code in `app/src/main/keepRules/rules.keep` and a release smoke test on a
device first. *Why:* R8 failures surface at runtime, on the build nobody runs daily.
<!-- origin: arch-spec BUILD-22 -->

**BUILD-23** — No product flavors by default; per-environment values are injected by the host from one DI
module (MOD-15). A project that adds flavors moves those values into the flavor and keeps the single
injection point. *Why:* flavors multiply every task and CI minute; one injection point keeps the switch local.
<!-- origin: arch-spec BUILD-23 -->

### Module graph check

**BUILD-24** — Every module gets `checkModuleGraph` from the library / application convention, hooked into
`check`: it fails on a project edge that breaks the module rules (MOD-01, MOD-07, MOD-09, MOD-10, MOD-11,
MOD-19) and names the rule. The rules are the pure `ModuleGraphRules` object, unit-tested with
`./gradlew :build-logic:convention:test`; the task only collects edges as strings at configuration time
(BUILD-14). *Why:* dependency direction is the load-bearing rule; a red build cannot be ignored, prose can.
<!-- origin: arch-spec BUILD-24 -->

### Lint and formatting

**BUILD-25** — Android Lint is configured by the conventions: `abortOnError`, XML + SARIF reports, and only
time-dependent checks disabled (`GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion`) plus
`OldTargetApi` (BUILD-35). Run it as `./gradlew :app:lintDebug` — the host checks every module
(`checkDependencies`). `app/lint-baseline.xml` was rendered empty: fix a finding, or suppress the one line with
`@SuppressLint` / `tools:ignore` and a comment saying why; never add it to the baseline. Extra Compose rules are
opt-in: `lintChecks(libs.compose.lints)` in the compose conventions. *Why:* a baseline that absorbs new
findings switches lint off silently; a finding that depends on the date trains everyone to ignore red.
<!-- origin: arch-spec BUILD-25 -->

**BUILD-26** — ktlint, through Spotless, formats `src/**/*.kt` and module `*.gradle.kts` by the root
`.editorconfig` (also read by the IDE): `./gradlew spotlessApply` fixes, `spotlessCheck` (part of `check`)
verifies. `.editorconfig` disables only import ordering and signature joining, which cannot hold for code
generated with install-time names. A ktlint upgrade is its own change plus one `spotlessApply`. *Why:* style
stops drifting between sessions and review diffs stay about behaviour.
<!-- origin: arch-spec BUILD-26 -->

## 9. Recipes

**Add a library**: add the version under the right `[versions]` header (with a pin reason if pinned), the
library under the right `[libraries]` header, then `implementation(libs.<alias>)` in the module that imports
it — never in a module that does not (MOD-02).

**Add a convention**: write `Android<Thing>ConventionPlugin` (default package, helpers as internal
extensions), register it in `build-logic/convention/build.gradle.kts`, add the catalog marker
`{{CONVENTION_PREFIX}}-android-<thing> = { id = "{{CONVENTION_PREFIX}}.android.<thing>", version = "unspecified" }`
(a module-role convention uses `.feature.<role>` / `-feature-<role>`), update the table in section 2 and this
skill's changelog. Never remove or repurpose an id: older build files still apply it (BUILD-09).

**Change what every feature module gets** (a test dependency for all UI modules, a new baseline edge): edit the
`feature.*` convention, not the feature build files.

**Verify a build change**: `./gradlew help` (configuration), then
`./gradlew spotlessApply` and `./gradlew assembleDebug testDebugUnitTest spotlessCheck checkModuleGraph :app:lintDebug` (plus `:build-logic:convention:test` after editing
`build-logic`);
run it twice — the second run must reuse the configuration cache.
