# Toolchain — upgrades and version-conditional gotchas (component: foundation)

The versions themselves live only in `gradle/libs.versions.toml` (with a pin reason above every held-back
entry); the spec's last known-good set is in `.arch-spec/spec/specs/toolchain.md`. Every gotcha below is
written version-conditionally so it visibly stops applying after an upgrade. BUILD-30 (never apply the
standalone Kotlin Android plugin on AGP with built-in Kotlin) is a golden rule in `SKILL.md`.

Contents: 1. Coupled groups · 2. Upgrading safely · 3. Gotchas · 4. Diagnosing a red build

---

## 1. Coupled groups

Move these together, never one member alone:

| Group | Why coupled |
|---|---|
| Kotlin + KSP + Compose compiler plugin + serialization plugin | share the Kotlin version; KSP's prefix must equal it (BUILD-33) |
| AGP + Hilt / Dagger | Hilt's Gradle plugin must support the AGP line (BUILD-34) |
| androidx.lifecycle (and other AndroidX) + `compileSdk` | some releases require a higher compile SDK (BUILD-35) |
| Kotlin + libraries compiled against a newer stdlib | metadata may be unreadable (BUILD-39) |

## 2. Upgrading safely

1. Branch; record `./gradlew assembleDebug testDebugUnitTest` green on the current set.
2. Move one coupled group at a time, in this order, building and testing after each: Gradle wrapper (run
   `./gradlew wrapper --gradle-version <v>` twice so the wrapper jar updates too) → Kotlin group → AGP →
   Hilt → AndroidX (check the minimum compile SDK) → everything else.
3. After each group: `./gradlew --stop`, then `./gradlew clean assembleDebug testDebugUnitTest`.
4. Re-read every gotcha below; strike the ones the upgrade invalidated with an override or a removal note
   (skills-maintenance, amend) and update the pin comments in the catalog in the same change.

## 3. Gotchas

**BUILD-31** — On AGP with the parameterless `CommonExtension`, convention code takes
`commonExtension: CommonExtension` and uses property access (`defaultConfig.apply { }`,
`buildFeatures.compose = true`), not the older lambda DSL. *Why:* convention code copied from older references
does not compile against that API.
<!-- origin: arch-spec BUILD-31 -->

**BUILD-32** — With built-in Kotlin and KSP, `gradle.properties` keeps `android.disallowKotlinSourceSets=false`
until a KSP release registers sources through the new API. *Why:* KSP still registers generated sources
through the legacy source-set DSL; without the flag Hilt code generation fails in every module using it.
<!-- origin: arch-spec BUILD-32 -->

**BUILD-33** — KSP's version is `<kotlin>-<ksp>` and its prefix equals the Kotlin version exactly; upgrade them
in one commit. *Why:* a mismatched KSP refuses to run or emits metadata the compiler cannot read.
<!-- origin: arch-spec BUILD-33 -->

**BUILD-34** — On AGP with built-in Kotlin, Hilt must be at least the release that supports it (older Hilt
Gradle plugins fail with "BaseExtension not found" — the catalog comment names the minimum), and the hilt
convention adds error-prone annotations as `compileOnly`. *Why:* Dagger's generated code references an
annotation it does not export, so modules without it fail to compile.
<!-- origin: arch-spec BUILD-34 -->

**BUILD-35** — An AndroidX release may require a `compileSdk` above `targetSdk`; that is fine. ProjectConfig's
compile SDK comment names the library that forces it, and the machine needs that SDK platform installed.
*Why:* compile and target SDK are independent — raising the target changes runtime behaviour and is a
deliberate decision; raising the compile SDK is not.
<!-- origin: arch-spec BUILD-35 -->

**BUILD-36** — The convention-side `Project.libs` accessor stays `internal` (BUILD-07). *Why:* a public
extension named `libs` on the build-logic classpath shadows the generated type-safe accessor in every module.
<!-- origin: arch-spec BUILD-36 -->

**BUILD-37** — Under KSP, a `@JvmInline value class` wrapping a primitive cannot be an injected type; use a
`data class` for single-value configs. *Why:* it erases to a bare primitive and KSP fails the whole module with
`invalid type parameter: long` and no file or line.
<!-- origin: arch-spec BUILD-37 -->

**BUILD-38** — Never write slash-star inside a KDoc or block comment (a path glob like `/v1/items/...` is
fine; with a star it is not). Kotlin block comments nest, so the comment swallows the rest of the file.
*Why:* under KSP this surfaces as a cascade of unresolved Hilt bindings with no file or line; the only tell is
an "Unclosed comment" on the file's last line in the IDE.
<!-- origin: arch-spec BUILD-38 -->

**BUILD-39** — A library compiled against a newer Kotlin stdlib can be unreadable by the project's compiler
("incompatible metadata version"). Pin such a library in the catalog with a comment and move it only together
with Kotlin. *Why:* the error names the library, not the Kotlin version that is the real constraint.
<!-- origin: arch-spec BUILD-39 -->

**BUILD-40** — `@Provides` functions in an `object` Hilt module are public (the module type itself may be
`internal`). *Why:* an `internal` member is name-mangled on the JVM and Dagger's generated Java cannot call
it; the failure appears only when the module gains its first such function.
<!-- origin: arch-spec BUILD-40 -->

**BUILD-41** — Configuration-cache-safe reads only: `providers.fileContents`, `providers.gradleProperty`,
`providers.environmentVariable` (BUILD-14). *Why:* untracked inputs make cached configurations stale without
any error.
<!-- origin: arch-spec BUILD-41 -->

**BUILD-42** — `hiltViewModel()` is imported from `androidx.hilt:hilt-lifecycle-viewmodel-compose`; the copy in
`hilt-navigation-compose` is deprecated. *Why:* the deprecated import compiles today and breaks on the next
androidx.hilt upgrade.
<!-- origin: arch-spec BUILD-42 -->

**BUILD-43** — On AGP with the `optimization { enable = … }` DSL, R8 is switched there (not with
`isMinifyEnabled`) and keep rules live where that AGP version reads them (`app/src/main/keepRules/` here);
re-check both on every AGP upgrade (BUILD-22). *Why:* rules in a location AGP no longer reads are silently
ignored.
<!-- origin: arch-spec BUILD-43 -->

**BUILD-44** — R classes are non-transitive: a module using another module's resources imports that `R`
explicitly (`import {{ROOT_PACKAGE}}.libandroid.R as LibR`). *Why:* code copied from older projects assumes
transitive `R` and fails with unresolved resource ids.
<!-- origin: arch-spec BUILD-44 -->

## 4. Diagnosing a red build

| Symptom | Usual cause |
|---|---|
| KSP / Hilt errors with no `file.kt:line` | BUILD-38 (slash-star in a comment) or BUILD-37 (value class) |
| "BaseExtension not found" | Hilt too old for the AGP line (BUILD-34) |
| "cannot be provided" in the host for a type from another module | that module lacks the hilt convention (DI-05) |
| `kotlinOptions` / `kotlin.sourceSets` unresolved or forbidden | built-in Kotlin (BUILD-30, BUILD-32) |
| "incompatible metadata version" | BUILD-39 |
| version unchanged after editing `version.properties` | a non-`providers` read (BUILD-14) |
| compile SDK platform missing | install the platform named in ProjectConfig (BUILD-35) |
