import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * `{{CONVENTION_PREFIX}}.feature.api` — a feature's contract module (`:x-ui-api`): the library convention plus
 * its one baseline edge, `api(project(":feature-api"))` (MOD-09). No Compose, no Hilt: every consumer of the
 * contract would pay for them.
 *
 * `:feature-api` belongs to the architecture component, but it is referenced only when a module applies this
 * plugin, so build-logic (and a foundation-only project) still builds without it.
 */
class AndroidFeatureApiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("{{CONVENTION_PREFIX}}.android.library")

        dependencies {
            // FeatureApi / FeaturesHolder appear in the contract's public signatures, hence `api`.
            add("api", project(":feature-api"))
        }
    }
}
