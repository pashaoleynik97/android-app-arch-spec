import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

/**
 * `checkModuleGraph` — fails the build when this module's declared project dependencies break the module rules
 * ([ModuleGraphRules]). Prose rules get broken; a red build does not (BUILD-24).
 *
 * Configuration-cache safe (BUILD-14): every input is a plain string collected while the project is configured;
 * the action never touches `Project`. The report file makes the task up to date when nothing changed.
 */
abstract class CheckModuleGraphTask : DefaultTask() {

    @get:Input
    abstract val modulePath: Property<String>

    /** Every project path in the build: telling a feature domain `:x` from infra needs to know `:x-ui` exists. */
    @get:Input
    abstract val allModulePaths: SetProperty<String>

    /** Declared project edges as `configuration|:path`. */
    @get:Input
    abstract val edges: ListProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val problems = ModuleGraphRules.violations(
            from = modulePath.get(),
            edges = edges.get().map(ModuleGraphRules.Edge::decode),
            allPaths = allModulePaths.get(),
        )
        report.get().asFile.writeText(problems.joinToString("\n", postfix = if (problems.isEmpty()) "" else "\n"))
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Module graph rules broken in ${modulePath.get()}:\n" +
                    problems.joinToString("\n") { "  - $it" } +
                    "\nSee specs/10-modules.md (or the application-architecture skill, modules reference).",
            )
        }
    }
}

/**
 * Registers `checkModuleGraph` on this module and hooks it into `check`. Called by the library and application
 * conventions, so every module is covered without any build-file line.
 */
internal fun Project.configureModuleGraphCheck() {
    // Project paths only (strings): reading them is safe at configuration time and serializable for the cache.
    val selfPath = path
    val allPaths = rootProject.allprojects.map { it.path }.toSet()
    val declaredEdges = provider {
        // Declared buckets only (implementation, api, testImplementation, …): resolvable classpaths repeat those
        // edges and AGP adds the module itself to its test classpaths.
        configurations.filter { !it.isCanBeResolved }.flatMap { configuration ->
            configuration.dependencies.withType(ProjectDependency::class.java)
                .map { ModuleGraphRules.Edge(configuration.name, it.path).encode() }
        }.distinct().sorted()
    }
    val task = tasks.register<CheckModuleGraphTask>("checkModuleGraph") {
        group = "verification"
        description = "Checks this module's project dependencies against the module rules (MOD-01, MOD-11, …)."
        modulePath.set(selfPath)
        allModulePaths.set(allPaths)
        edges.set(declaredEdges)
        report.set(layout.buildDirectory.file("reports/module-graph/violations.txt"))
    }
    tasks.matching { it.name == "check" }.configureEach { dependsOn(task) }
}
