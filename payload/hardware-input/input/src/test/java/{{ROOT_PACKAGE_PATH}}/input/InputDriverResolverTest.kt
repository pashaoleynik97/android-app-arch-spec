package {{ROOT_PACKAGE}}.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import {{ROOT_PACKAGE}}.coretesting.RecordingReporter
import {{ROOT_PACKAGE}}.input.api.Detection
import {{ROOT_PACKAGE}}.input.api.InputDriverId

/** The resolution rules decide whether a device has working input at all, so each branch is pinned. */
class InputDriverResolverTest {

    @get:Rule
    val reporter = RecordingReporter()

    private val vendor = InputDriverId("vendor")
    private val debug = InputDriverId("debug")

    @Test
    fun `auto picks the highest-priority supported provider`() {
        val resolver = InputDriverResolver(
            setOf(FakeInputDriverProvider(debug, priority = -100), FakeInputDriverProvider(vendor, priority = 100)),
        )

        assertEquals(vendor, resolver.resolve(InputPreference.Auto)?.id)
    }

    @Test
    fun `auto skips an unsupported provider even at a higher priority`() {
        val absent = FakeInputDriverProvider(vendor, priority = 100, detection = Detection.Unsupported)
        val fallback = FakeInputDriverProvider(debug, priority = -100)

        assertEquals(debug, InputDriverResolver(setOf(absent, fallback)).resolve(InputPreference.Auto)?.id)
    }

    @Test
    fun `auto yields no driver when nothing is supported or nothing is registered`() {
        assertNull(
            InputDriverResolver(setOf(FakeInputDriverProvider(vendor, detection = Detection.Unsupported)))
                .resolve(InputPreference.Auto),
        )
        assertNull(InputDriverResolver(emptySet()).resolve(InputPreference.Auto))
    }

    @Test
    fun `forced uses that provider without consulting detection or priority`() {
        val forced = FakeInputDriverProvider(vendor, priority = -1, detection = Detection.Unsupported)
        val other = FakeInputDriverProvider(debug, priority = 100)

        assertEquals(vendor, InputDriverResolver(setOf(forced, other)).resolve(InputPreference.Forced(vendor))?.id)
    }

    @Test
    fun `forced falls back to auto, loudly, when the id is no longer registered`() {
        val remaining = FakeInputDriverProvider(debug, priority = 10)

        val driver = InputDriverResolver(setOf(remaining)).resolve(InputPreference.Forced(InputDriverId("gone")))

        assertEquals(debug, driver?.id)
        assertTrue(reporter.exceptions.any { "'gone' is not registered" in it.message.orEmpty() })
    }

    @Test
    fun `disabled yields no driver and creates none`() {
        val provider = FakeInputDriverProvider(vendor)

        assertNull(InputDriverResolver(setOf(provider)).resolve(InputPreference.Disabled))
        assertTrue(provider.created.isEmpty())
    }

    @Test
    fun `detectAll reports every provider, highest priority first`() {
        val high = FakeInputDriverProvider(vendor, priority = 100)
        val low = FakeInputDriverProvider(debug, priority = -100, detection = Detection.Unsupported)

        val verdicts = InputDriverResolver(setOf(low, high)).detectAll()

        assertEquals(listOf(vendor, debug), verdicts.map { it.first.id })
        assertEquals(Detection.Unsupported, verdicts[1].second)
    }
}
