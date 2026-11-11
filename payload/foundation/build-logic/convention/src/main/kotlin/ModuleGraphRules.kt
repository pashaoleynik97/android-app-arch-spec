/**
 * The module dependency-direction rules of the architecture spec (MOD-01, MOD-07, MOD-09, MOD-10, MOD-11, MOD-19),
 * as a pure function over module paths, so the rules are unit-tested without Gradle and the task that runs them
 * (`checkModuleGraph`) only collects edges.
 *
 * Modules are classified by name (MOD-20), using the last path segment:
 * - host `app`; kernel `lib-common` < `feature-api` < `lib-android` (an edge may only point to a lower kernel);
 *   test kit `core-testing`;
 * - feature contract `x-ui-api`, feature UI `x-ui`, feature domain `x` (only when `x-ui` or `x-ui-api` exists);
 * - every other module is infra (`network`, `auth`, contract-only leaves like `input-api`, debug-only drivers).
 *
 * Why names and not markers: the naming rule is already MUST, so the check needs no extra configuration in any
 * module and a renamed module is classified by the same rule an agent reads.
 */
internal object ModuleGraphRules {

    enum class Kind { HOST, KERNEL, TEST_KIT, FEATURE_CONTRACT, FEATURE_UI, FEATURE_DOMAIN, INFRA }

    data class Module(val path: String, val kind: Kind, val feature: String? = null) {
        val isFeature: Boolean get() = feature != null
    }

    /** One declared project dependency: the configuration it is declared in and the target module path. */
    data class Edge(val configuration: String, val target: String) {
        /** Test classpaths (`testImplementation`, `androidTestImplementation`, `kspTest…`) follow looser rules. */
        val isTest: Boolean get() = configuration.startsWith("test") || configuration.contains("Test")

        /** Serialized as `configuration|:path`, the form the Gradle task stores as an input. */
        fun encode(): String = "$configuration|$target"

        companion object {
            fun decode(raw: String): Edge = Edge(raw.substringBefore('|'), raw.substringAfter('|'))
        }
    }

    private val KERNEL_RANK = mapOf("lib-common" to 0, "feature-api" to 1, "lib-android" to 2)
    private const val HOST = "app"
    private const val TEST_KIT = "core-testing"

    fun classify(path: String, allPaths: Set<String>): Module {
        val name = path.substringAfterLast(':')
        val parent = path.substringBeforeLast(':', missingDelimiterValue = "")
        fun sibling(n: String) = "$parent:$n"
        return when {
            name == HOST -> Module(path, Kind.HOST)
            name in KERNEL_RANK -> Module(path, Kind.KERNEL)
            name == TEST_KIT -> Module(path, Kind.TEST_KIT)
            name.endsWith("-ui-api") -> Module(path, Kind.FEATURE_CONTRACT, name.removeSuffix("-ui-api"))
            name.endsWith("-ui") -> Module(path, Kind.FEATURE_UI, name.removeSuffix("-ui"))
            sibling("$name-ui") in allPaths || sibling("$name-ui-api") in allPaths ->
                Module(path, Kind.FEATURE_DOMAIN, name)
            else -> Module(path, Kind.INFRA)
        }
    }

    /**
     * Every rule violation of [from]'s declared [edges], one human-readable line each, starting with the rule id.
     * Empty when the module is clean.
     */
    fun violations(from: String, edges: List<Edge>, allPaths: Set<String>): List<String> {
        val source = classify(from, allPaths)
        return edges
            .filter { it.target != from }
            .mapNotNull { edge -> violation(source, classify(edge.target, allPaths), edge) }
            .distinct()
    }

    private fun violation(from: Module, to: Module, edge: Edge): String? {
        val at = "${from.path} depends on ${to.path} (${edge.configuration})"
        // Rules that hold on every classpath, test ones included.
        if (to.kind == Kind.HOST) return "MOD-01: $at — nothing depends on the host."
        if (from.isFeature && to.isFeature && from.feature != to.feature) {
            return if (from.kind == Kind.FEATURE_DOMAIN && to.kind == Kind.FEATURE_DOMAIN) {
                "MOD-19: $at — sibling domains never depend on each other. Declare a port in " +
                    "${from.path} and let the host bind it."
            } else {
                "MOD-11: $at (feature → another feature). Use a host seam (MOD-18)."
            }
        }
        if (edge.isTest) return null

        // Main and debug/release classpaths.
        if (to.kind == Kind.TEST_KIT) return "MOD-07: $at — ${to.path} is testImplementation only."
        return when (from.kind) {
            Kind.HOST -> null
            Kind.KERNEL ->
                if (to.kind == Kind.KERNEL && rank(to) < rank(from)) {
                    null
                } else {
                    "MOD-01: $at — kernel modules depend only on lower kernel modules."
                }
            Kind.TEST_KIT ->
                if (to.kind == Kind.KERNEL) {
                    null
                } else {
                    "MOD-07: $at — the test kit holds kernel doubles only."
                }
            Kind.FEATURE_CONTRACT ->
                if (to.kind == Kind.KERNEL && rank(to) <= KERNEL_RANK.getValue("feature-api")) {
                    null
                } else {
                    "MOD-09: $at — a contract module depends on :feature-api only."
                }
            Kind.FEATURE_DOMAIN -> when {
                to.kind == Kind.FEATURE_UI || to.kind == Kind.FEATURE_CONTRACT ->
                    "MOD-01: $at — a domain module never depends on UI modules."
                to.kind == Kind.KERNEL && rank(to) == KERNEL_RANK.getValue("lib-android") ->
                    "MOD-10: $at — a domain module uses no Compose; depend on :lib-common."
                else -> null
            }
            Kind.FEATURE_UI -> null
            Kind.INFRA ->
                if (to.isFeature) {
                    "MOD-01: $at — infra sits below features; invert it with a port (MOD-19)."
                } else {
                    null
                }
        }
    }

    private fun rank(module: Module): Int = KERNEL_RANK.getValue(module.path.substringAfterLast(':'))
}
