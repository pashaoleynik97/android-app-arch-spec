package {{ROOT_PACKAGE}}.engineering.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringRow
import {{ROOT_PACKAGE}}.engineering.ui.menu.EngineeringMenuFilter
import {{ROOT_PACKAGE}}.engineering.ui.menu.EngineeringMenuSection

class EngineeringMenuFilterTest {

    private val sections = listOf(
        EngineeringMenuSection(
            key = "device",
            title = "Device",
            rows = listOf(
                EngineeringRow("device", "Device", "Example Phone 7"),
                EngineeringRow("android", "Android", "14 (API 34)"),
            ),
        ),
        EngineeringMenuSection(
            key = "logging",
            title = "Logging",
            rows = listOf(EngineeringRow("verbose", "Verbose logs", "Print request bodies")),
        ),
    )

    @Test
    fun `a blank query returns the sections unchanged`() {
        assertEquals(sections, EngineeringMenuFilter.filter(sections, ""))
        assertEquals(sections, EngineeringMenuFilter.filter(sections, "   "))
    }

    @Test
    fun `matches titles and descriptions, ignoring case and surrounding whitespace`() {
        val byDescription = EngineeringMenuFilter.filter(sections, "  phone ")
        assertEquals(listOf("device"), byDescription.flatMap { it.rows }.map { it.key })

        val byTitle = EngineeringMenuFilter.filter(sections, "VERBOSE")
        assertEquals(listOf("verbose"), byTitle.flatMap { it.rows }.map { it.key })
    }

    @Test
    fun `runtime values in descriptions are searchable`() {
        assertEquals(listOf("android"), EngineeringMenuFilter.filter(sections, "api 34").flatMap { it.rows }.map { it.key })
    }

    @Test
    fun `a section left without rows is dropped, header included`() {
        val result = EngineeringMenuFilter.filter(sections, "bodies")

        assertEquals(listOf("logging"), result.map { it.key })
    }

    @Test
    fun `no match yields no sections`() {
        assertTrue(EngineeringMenuFilter.filter(sections, "bluetooth").isEmpty())
    }
}
