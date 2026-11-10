import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

/**
 * `{{CONVENTION_PREFIX}}.android.library.compose` — Compose for a library module.
 * Apply it **after** `{{CONVENTION_PREFIX}}.android.library` (it reads the `LibraryExtension` that one creates).
 */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        configureAndroidCompose(extensions.getByType<LibraryExtension>())
    }
}
