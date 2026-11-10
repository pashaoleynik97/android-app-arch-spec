import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

/**
 * `{{CONVENTION_PREFIX}}.android.application.compose` — Compose for the `:app` module, configured exactly like
 * the library modules so the host never re-declares the BOM and Compose artifacts by hand.
 * Apply it **after** `{{CONVENTION_PREFIX}}.android.application`.
 */
class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        configureAndroidCompose(extensions.getByType<ApplicationExtension>())
    }
}
