# Toolchain — last known-good set, safe upgrades, version-conditional gotchas

This is a **recommendation**, not a pin. It is the newest combination this spec was built and tested against.
The installer offers either this set (default) or "latest stable, then re-verify every gotcha below". This file
and `payload/foundation/gradle/libs.versions.toml` are the only places in the spec where version numbers appear.

Gotchas carry rule IDs **BUILD-30 and up** (area `BUILD`, see `20-build-system.md`). Each is written
version-conditionally so it stays true, or visibly stops applying, after an upgrade.

---

## 1. Last known-good set

| Item | Version | Notes |
|---|---|---|
| Gradle wrapper | 9.4.1 | `-bin` distribution, `validateDistributionUrl=true` |
| Gradle daemon JVM | 21 | via `gradle/gradle-daemon-jvm.properties` + the foojay toolchain resolver plugin 1.0.0 in `settings.gradle.kts` |
| JDK on CI runners | 17 | `{{JDK_CI}}`; the daemon toolchain is provisioned separately |
| Java / Kotlin bytecode | 11 | `{{JVM_TARGET}}`; AGP aligns Kotlin `jvmTarget` with `compileOptions` |
| Android Gradle Plugin | 9.2.1 | built-in Kotlin (BUILD-30) |
| Kotlin | 2.2.10 | Compose compiler plugin and serialization plugin share this version |
| KSP | 2.2.10-2.0.2 | prefix must equal Kotlin (BUILD-33) |
| Hilt / Dagger | 2.60 | minimum on AGP 9 (BUILD-34) |
| androidx.hilt (`hilt-lifecycle-viewmodel-compose`, `hilt-navigation-compose`) | 1.4.0 | `hiltViewModel()` lives in `hilt-lifecycle-viewmodel-compose` |
| error_prone_annotations | 2.41.0 | `compileOnly` in the Hilt convention (BUILD-34) |
| Compose BOM | 2026.02.01 | |
| navigation-compose | 2.9.8 | string routes (type-safe routes rejected, ADR-0011); Navigation 3 is out of scope |
| androidx.lifecycle (runtime-ktx, runtime-compose, process, viewmodel-compose) | 2.11.0 | forces `compileSdk 37` (BUILD-35) |
| activity-compose | 1.8.0 | |
| core-ktx | 1.10.1 | |
| kotlinx-coroutines | 1.10.2 | `-android` and `-test` |
| javax.inject | 1 | |
| Retrofit (+ kotlinx-serialization converter) | 2.11.0 | Recommended `network` component |
| OkHttp BOM (okhttp, logging-interceptor, mockwebserver) | 4.12.0 | Recommended `network` component |
| kotlinx-serialization-json | 1.8.0 | |
| Coil 3 | 3.3.0 | pinned to the Kotlin line (BUILD-39); Recommended `images` component |
| androidx.startup (`startup-runtime`) | 1.2.0 | process-start initializers of Recommended `media-store` and `images` |
| Spotless Gradle plugin | 8.10.4 | runs ktlint (BUILD-26); configuration-cache compatible |
| ktlint | 1.8.0 | formatting rules of every Kotlin file; bump on its own, then `spotlessApply` once |
| Slack compose-lints | 1.6.1 | catalog entry only, opt-in (BUILD-25) |
| JUnit | 4.13.2 | |
| Turbine | 1.2.0 | |
| SDK levels | `compileSdk 37`, `targetSdk 36`, `minSdk 24` | `{{COMPILE_SDK}}` / `{{TARGET_SDK}}` / `{{MIN_SDK}}` |

`minSdk 24` without core-library desugaring has no `java.time`; see KERNEL-19 for the decision this forces.

## 2. How to upgrade safely

1. **Branch, clean build first.** Record `./gradlew assembleDebug testDebugUnitTest` green on the current set.
2. **Move one coupled group at a time**, in this order, building and testing after each:
   1. Gradle wrapper (`./gradlew wrapper --gradle-version …`, run twice so the wrapper jar updates too);
   2. **Kotlin + KSP + Compose compiler + serialization plugin together** (they share the Kotlin version; KSP's
      prefix must match, BUILD-33), then re-check BUILD-39 for every library built against a Kotlin stdlib;
   3. AGP (re-check BUILD-30, BUILD-31, BUILD-32, BUILD-34 and the keep-rule location for BUILD-22);
   4. Hilt / Dagger and androidx.hilt;
   5. AndroidX (Compose BOM, lifecycle, navigation, activity) — check whether a new minimum `compileSdk` is
      required (BUILD-35);
   6. everything else.
3. **After each group**: `./gradlew --stop`, then `./gradlew clean assembleDebug testDebugUnitTest
   --configuration-cache`; a KSP or Hilt failure with no file/line is usually BUILD-37 or BUILD-38, not the
   upgrade itself.
4. **Rewrite every gotcha** below that the upgrade invalidated (strike it as `~~BUILD-xx~~ (obsolete since …)` —
   IDs are never reused) and update the pin comments in the catalog.
5. **Update the table above** and the catalog in the same change; nothing else in the spec mentions versions.

## 3. Version-conditional gotchas

**BUILD-30** — **On AGP ≥ 9, Kotlin is built in.** Never apply `org.jetbrains.kotlin.android` (and do not
declare it in the catalog, BUILD-12); the Android plugin compiles Kotlin. Compiler options use
`kotlin { compilerOptions { } }`; the `kotlinOptions {}` DSL no longer exists. Other Kotlin compiler plugins
(Compose, serialization) are still applied by id.
*Why:* applying the standalone plugin on top of built-in Kotlin fails configuration or registers sources twice.

**BUILD-31** — **On AGP ≥ 9, `CommonExtension` has no type parameters.** In convention code, take
`commonExtension: CommonExtension` and use property access (`defaultConfig.apply { }`,
`compileOptions.apply { }`, `buildFeatures.compose = true`), not the older lambda DSL
`commonExtension.defaultConfig { }`.
*Why:* convention code copied from older references does not compile against the new API.

**BUILD-32** — **With built-in Kotlin and KSP**, `gradle.properties` needs `android.disallowKotlinSourceSets=false`.
*Why:* built-in Kotlin forbids the legacy `kotlin.sourceSets` DSL, but KSP still registers its generated sources
through it during the transition; without the flag, Hilt code generation fails. Remove the flag once a KSP
release registers sources through the new API.

**BUILD-33** — **KSP's version prefix MUST equal the Kotlin version** (`<kotlin>-<ksp>`). Upgrade them in one
commit.
*Why:* a mismatched KSP refuses to run or produces metadata the compiler cannot read.

**BUILD-34** — **On AGP ≥ 9, Hilt MUST be ≥ 2.60** (older Hilt Gradle plugins fail with "BaseExtension not
found"). The Hilt convention adds `com.google.errorprone:error_prone_annotations` as `compileOnly`.
*Why:* Dagger's generated components on this line reference `@CanIgnoreReturnValue` without propagating the
annotation dependency, so compilation fails in modules that do not happen to have it.

**BUILD-35** — **androidx.lifecycle ≥ 2.11 requires `compileSdk 37`.** `targetSdk` may stay one level lower;
`ProjectConfig.COMPILE_SDK` carries a comment naming the library that forces it (BUILD-06). The machine needs
the matching SDK platform installed.
*Why:* `compileSdk` and `targetSdk` are independent; raising `targetSdk` changes runtime behaviour and must be
a deliberate decision, raising `compileSdk` does not.

**BUILD-36** — **The convention-side `Project.libs` accessor MUST be `internal`** (BUILD-07).
*Why:* on current Gradle a public extension named `libs` on the build-logic classpath shadows the generated
type-safe accessor in every module's build script.

**BUILD-37** — **Under KSP, a `@JvmInline value class` wrapping a primitive cannot be an injected type.** Use a
`data class` for single-value configs (`data class StoreConfig(val staleAfterMillis: Long)`). A value class over
`String` works (erases to a reference type) but `data class` is the uniform rule.
*Why:* the class erases to a bare `long` and KSP fails the whole module with `invalid type parameter: long`
and no file or line.

**BUILD-38** — **Never write the two characters slash-star inside a KDoc or block comment** (e.g. a path glob
like `` `/v1/items/*` `` — write `` `/v1/items/...` ``). Kotlin block comments nest, so the comment swallows the
rest of the file.
*Why:* under KSP2 this surfaces as a misleading "could not be resolved" cascade over every Hilt binding in the
module and bogus serialization opt-in errors, with no `file.kt:line`; the only tell is the IDE's
"Unclosed comment" on the last line of the file.

**BUILD-39** — **Libraries compiled against a newer Kotlin stdlib can be unreadable by an older compiler.** Coil 3
releases after 3.3.0 pull a stdlib whose metadata a Kotlin 2.2.x compiler cannot read; Coil is pinned with a
catalog comment and moves only together with the project's Kotlin version. Apply the same check to any library
upgrade that fails with "incompatible metadata version".
*Why:* the error points at the library, not at the Kotlin version that is actually the constraint.

**BUILD-40** — **`@Provides` functions in an `object` Hilt module MUST be public.** An `internal` member is
name-mangled on the JVM and Dagger's generated Java cannot call it. (The module type itself may be `internal`.)
*Why:* the failure appears only when a module first gains such a function, far from the cause.

**BUILD-41** — **Configuration-cache-safe reads only** (BUILD-14): `providers.fileContents(…)`,
`providers.gradleProperty(…)`, `providers.environmentVariable(…)`; never `File.readText()` or
`System.getenv()` at configuration time.
*Why:* on Gradle ≥ 8 with the configuration cache on, untracked inputs make cached configurations stale.

**BUILD-42** — **`hiltViewModel()` comes from `androidx.hilt:hilt-lifecycle-viewmodel-compose`** on androidx.hilt
≥ 1.3; the copy in `hilt-navigation-compose` is deprecated.
*Why:* importing the deprecated one compiles today and breaks on the next androidx.hilt upgrade.

**BUILD-43** — **On AGP ≥ 9, R8 is configured with the `optimization { enable = … }` DSL** (replacing
`isMinifyEnabled`), and keep rules live in the AGP-expected location for that version
(`src/main/keepRules/*.keep` on the known-good set). On that set, `optimization { enable = true }` also
requires `android.r8.gradual.support=true` in `gradle.properties`. Without it the build fails with *"Cannot use
optimization.enable=true without setting android.r8.gradual.support flag"*. Re-check all three on every AGP upgrade
(BUILD-22).
*Why:* rules in a location AGP no longer reads are silently ignored.

**BUILD-44** — **Non-transitive R classes are the AGP default.** A module referencing another module's resources
imports that module's `R` explicitly (`import {{ROOT_PACKAGE}}.libandroid.R as LibR`).
*Why:* code copied from older projects assumes transitive `R` and fails with unresolved resource ids.
