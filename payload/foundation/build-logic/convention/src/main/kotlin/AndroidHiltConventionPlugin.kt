import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `{{CONVENTION_PREFIX}}.android.hilt` — apply in every module that declares `@Inject`, `@Module` or
 * `@HiltViewModel` types, so KSP generates their factories inside that module.
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("com.google.devtools.ksp")
            apply("com.google.dagger.hilt.android")
        }

        dependencies {
            add("implementation", libs.findLibrary("hilt-android").get())
            add("ksp", libs.findLibrary("hilt-compiler").get())
            // Dagger's generated components reference @CanIgnoreReturnValue but do not export
            // error_prone_annotations, so it must be on the compile classpath explicitly.
            add("compileOnly", libs.findLibrary("errorprone-annotations").get())
        }
    }
}
