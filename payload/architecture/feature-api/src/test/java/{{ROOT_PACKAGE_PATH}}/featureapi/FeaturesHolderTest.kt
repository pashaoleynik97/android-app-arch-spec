package {{ROOT_PACKAGE}}.featureapi

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FeaturesHolderTest {

    private class FirstFeature : FeatureApi {
        override fun registerGraph(navGraphBuilder: NavGraphBuilder, navController: NavHostController) = Unit
    }

    private class SecondFeature : FeatureApi {
        override fun registerGraph(navGraphBuilder: NavGraphBuilder, navController: NavHostController) = Unit
    }

    @After
    fun tearDown() = FeaturesHolder.clear()

    @Test
    fun `features come back in registration order`() {
        val first = FirstFeature().also { it.addToRegistry() }
        val second = SecondFeature().also { it.addToRegistry() }

        assertEquals(listOf(first, second), FeaturesHolder.features())
    }

    @Test
    fun `registering the same feature type twice keeps one entry, the newest`() {
        FirstFeature().addToRegistry()
        val replacement = FirstFeature().also { it.addToRegistry() }

        assertEquals(1, FeaturesHolder.features().size)
        assertSame(replacement, FeaturesHolder.features().single())
    }
}
