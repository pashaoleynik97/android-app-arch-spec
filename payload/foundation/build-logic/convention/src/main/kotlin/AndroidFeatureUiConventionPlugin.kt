import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * `{{CONVENTION_PREFIX}}.feature.ui` — a feature's UI module (`:x-ui`): library + Compose + Hilt conventions and
 * the edges every feature UI needs: `:lib-android` (theme, kit, BaseViewModel, which re-exports `:lib-common` and
 * `:feature-api`), navigation-compose for `XFeatureImpl`, `hiltViewModel()`, and `:core-testing` for tests.
 *
 * The module's build file keeps only its namespace and its own edges (`:x-ui-api`, `:x`, infra it uses), so a
 * change to what every feature UI needs is made here once instead of in every scaffolded feature.
 * The kernel modules belong to the architecture component and are referenced only when a module applies this.
 */
class AndroidFeatureUiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            // Order matters: the Compose convention configures the extension the library convention creates.
            apply("{{CONVENTION_PREFIX}}.android.library")
            apply("{{CONVENTION_PREFIX}}.android.library.compose")
            apply("{{CONVENTION_PREFIX}}.android.hilt")
        }

        dependencies {
            add("implementation", project(":lib-android"))
            add("implementation", libs.findLibrary("androidx-navigation-compose").get())
            add("implementation", libs.findLibrary("hilt-lifecycle-viewmodel-compose").get())
            // Shared fakes for the kernel seams; test classpath only (MOD-07).
            add("testImplementation", project(":core-testing"))
        }
    }
}
