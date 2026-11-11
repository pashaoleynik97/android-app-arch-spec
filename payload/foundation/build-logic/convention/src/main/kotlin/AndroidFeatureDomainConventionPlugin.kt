import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * `{{CONVENTION_PREFIX}}.feature.domain` — a feature's domain module (`:x`): library + Hilt conventions,
 * `:lib-common` (Reporter, dispatcher and clock seams), `javax.inject` for `@Inject` constructors, and
 * `:core-testing` for tests. Deliberately no Compose: the domain stays JVM-testable without UI (MOD-10).
 *
 * The module's build file adds only infra it uses (e.g. `:network`). The kernel modules belong to the
 * architecture component and are referenced only when a module applies this plugin.
 */
class AndroidFeatureDomainConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("{{CONVENTION_PREFIX}}.android.library")
            apply("{{CONVENTION_PREFIX}}.android.hilt")
        }

        dependencies {
            add("implementation", project(":lib-common"))
            add("implementation", libs.findLibrary("javax-inject").get())
            // Shared fakes for the kernel seams; test classpath only (MOD-07).
            add("testImplementation", project(":core-testing"))
        }
    }
}
