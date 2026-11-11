import com.android.build.api.dsl.CommonExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Android Lint defaults for every module (BUILD-25). Lint runs as `./gradlew :app:lintDebug`: the host checks its
 * dependencies too (`checkDependencies`), so one task covers every module and one baseline lives in `:app`.
 *
 * Disabled checks are the ones whose result depends on the day the build runs, not on the code: "a newer
 * version exists" findings turn a green build red without a change. Version moves are deliberate, grouped
 * upgrades (see the toolchain reference), and `targetSdk` sits below `compileSdk` on purpose (BUILD-35).
 */
internal fun Project.configureAndroidLint(commonExtension: CommonExtension, isHost: Boolean) {
    commonExtension.lint.apply {
        abortOnError = true
        xmlReport = true
        sarifReport = true
        disable += setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
        )
        if (isHost) {
            checkDependencies = true
            // Pre-existing findings only (created at install, BUILD-25); new code never adds to it.
            baseline = file("lint-baseline.xml")
        }
    }
}

/**
 * Formatting for every module (BUILD-26): ktlint through Spotless on Kotlin sources and the module's build script,
 * configured by the root `.editorconfig` (shared with the IDE). Spotless adds `spotlessCheck` to `check`;
 * `./gradlew spotlessApply` fixes what it reports.
 *
 * The ktlint version comes from the catalog: it decides the formatting of every file, so it moves only on purpose.
 */
internal fun Project.configureSpotless() {
    pluginManager.apply("com.diffplug.spotless")
    val ktlintVersion = libs.findVersion("ktlint").get().requiredVersion
    val editorConfig = rootProject.layout.projectDirectory.file(".editorconfig").asFile
    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(ktlintVersion).setEditorConfigPath(editorConfig)
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion).setEditorConfigPath(editorConfig)
        }
    }
}
