import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `{{CONVENTION_PREFIX}}.android.application` — the `:app` module: SDK levels, Java target, test defaults, lint
 * defaults (BUILD-25), ktlint formatting (BUILD-26) and the `checkModuleGraph` task (BUILD-24).
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // AGP 9 has built-in Kotlin — never add `org.jetbrains.kotlin.android`.
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            configureUnitTests(this)
            configureAndroidLint(this, isHost = true)
            defaultConfig.targetSdk = ProjectConfig.TARGET_SDK
        }
        configureModuleGraphCheck()
        configureSpotless()
    }
}
