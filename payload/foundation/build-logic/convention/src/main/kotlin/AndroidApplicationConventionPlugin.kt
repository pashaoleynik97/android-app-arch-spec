import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `{{CONVENTION_PREFIX}}.android.application` — the `:app` module: SDK levels, Java target, test defaults. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // AGP 9 has built-in Kotlin — never add `org.jetbrains.kotlin.android`.
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            configureUnitTests(this)
            defaultConfig.targetSdk = ProjectConfig.TARGET_SDK
        }
    }
}
