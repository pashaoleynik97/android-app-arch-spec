import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleGraphRulesTest {

    private val all = setOf(
        ":app", ":lib-common", ":feature-api", ":lib-android", ":core-testing",
        ":home-ui-api", ":home-ui", ":home",
        ":orders-ui-api", ":orders-ui", ":orders",
        ":engineering-ui-api", ":engineering-ui",
        ":network", ":auth", ":input-api", ":input",
    )

    private fun check(from: String, vararg edges: Pair<String, String>): List<String> =
        ModuleGraphRules.violations(from, edges.map { ModuleGraphRules.Edge(it.first, it.second) }, all)

    private fun assertRule(rule: String, problems: List<String>) {
        assertEquals("expected one violation, got $problems", 1, problems.size)
        assertTrue("expected $rule, got ${problems.single()}", problems.single().startsWith("$rule:"))
    }

    @Test
    fun `classifies by MOD-20 names`() {
        fun kind(path: String) = ModuleGraphRules.classify(path, all).kind
        assertEquals(ModuleGraphRules.Kind.HOST, kind(":app"))
        assertEquals(ModuleGraphRules.Kind.KERNEL, kind(":lib-android"))
        assertEquals(ModuleGraphRules.Kind.TEST_KIT, kind(":core-testing"))
        assertEquals(ModuleGraphRules.Kind.FEATURE_CONTRACT, kind(":home-ui-api"))
        assertEquals(ModuleGraphRules.Kind.FEATURE_UI, kind(":home-ui"))
        assertEquals(ModuleGraphRules.Kind.FEATURE_DOMAIN, kind(":home"))
        // A contract-only leaf and a module without a UI sibling are infra, not features.
        assertEquals(ModuleGraphRules.Kind.INFRA, kind(":input-api"))
        assertEquals(ModuleGraphRules.Kind.INFRA, kind(":network"))
    }

    @Test
    fun `the shipped graph is legal`() {
        assertEquals(emptyList<String>(), check(":lib-android", "api" to ":lib-common", "api" to ":feature-api"))
        assertEquals(emptyList<String>(), check(":feature-api", "api" to ":lib-common"))
        assertEquals(emptyList<String>(), check(":core-testing", "api" to ":lib-common"))
        assertEquals(emptyList<String>(), check(":home-ui-api", "api" to ":feature-api"))
        assertEquals(
            emptyList<String>(),
            check(
                ":home-ui",
                "implementation" to ":lib-android",
                "implementation" to ":home-ui-api",
                "implementation" to ":home",
                "testImplementation" to ":core-testing",
            ),
        )
        assertEquals(emptyList<String>(), check(":home", "implementation" to ":lib-common", "implementation" to ":network"))
        assertEquals(emptyList<String>(), check(":engineering-ui", "implementation" to ":input"))
        assertEquals(emptyList<String>(), check(":auth", "api" to ":network", "implementation" to ":lib-common"))
        assertEquals(emptyList<String>(), check(":input", "api" to ":input-api", "implementation" to ":lib-android"))
        assertEquals(
            emptyList<String>(),
            check(":app", "implementation" to ":home-ui", "implementation" to ":orders-ui", "debugImplementation" to ":input"),
        )
    }

    @Test
    fun `nothing depends on the host`() {
        assertRule("MOD-01", check(":home-ui", "implementation" to ":app"))
        assertRule("MOD-01", check(":home-ui", "testImplementation" to ":app"))
    }

    @Test
    fun `kernel depends only on lower kernel modules`() {
        assertRule("MOD-01", check(":lib-common", "implementation" to ":home"))
        assertRule("MOD-01", check(":lib-common", "implementation" to ":network"))
        assertRule("MOD-01", check(":lib-common", "implementation" to ":lib-android"))
    }

    @Test
    fun `features never depend on another feature`() {
        assertRule("MOD-11", check(":orders-ui", "implementation" to ":home"))
        assertRule("MOD-11", check(":orders-ui", "implementation" to ":home-ui-api"))
        assertRule("MOD-11", check(":orders-ui", "testImplementation" to ":home-ui"))
        assertRule("MOD-19", check(":orders", "implementation" to ":home"))
    }

    @Test
    fun `domain never sees UI`() {
        assertRule("MOD-01", check(":home", "implementation" to ":home-ui-api"))
        assertRule("MOD-10", check(":home", "implementation" to ":lib-android"))
    }

    @Test
    fun `contract depends on feature-api only`() {
        assertRule("MOD-09", check(":home-ui-api", "implementation" to ":lib-android"))
        assertRule("MOD-09", check(":home-ui-api", "implementation" to ":network"))
    }

    @Test
    fun `test kit stays on test classpaths`() {
        assertRule("MOD-07", check(":home", "implementation" to ":core-testing"))
        assertRule("MOD-07", check(":core-testing", "api" to ":network"))
    }

    @Test
    fun `infra never depends on features`() {
        assertRule("MOD-01", check(":network", "implementation" to ":home"))
    }

    @Test
    fun `edges round-trip through their encoded form`() {
        val edge = ModuleGraphRules.Edge("testDebugImplementation", ":core-testing")
        assertEquals(edge, ModuleGraphRules.Edge.decode(edge.encode()))
        assertTrue(edge.isTest)
        assertTrue(ModuleGraphRules.Edge("kspTest", ":x").isTest)
        assertTrue(!ModuleGraphRules.Edge("debugImplementation", ":x").isTest)
    }
}
