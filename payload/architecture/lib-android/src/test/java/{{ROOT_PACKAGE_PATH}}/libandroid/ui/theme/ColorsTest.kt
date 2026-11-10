package {{ROOT_PACKAGE}}.libandroid.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorsTest {

    @Test
    fun `hex answers parse into opaque colours`() {
        assertEquals(Color(0xFF3D5AFE), hexColor("#3D5AFE"))
        assertEquals(Color(0xFFFF5500), hexColor("#ff5500"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a malformed hex fails loudly`() {
        hexColor("3D5AFE")
    }

    @Test
    fun `content on a light colour is dark and on a dark colour is white`() {
        assertEquals(Color(0xFFFFFFFF), contentColorOn(Color(0xFF1A237E)))
        assertNotEquals(Color(0xFFFFFFFF), contentColorOn(Color(0xFFFFEB3B)))
    }

    @Test
    fun `light and dark sets share role names but differ in surfaces`() {
        val light = light{{THEME_PREFIX}}Colors()
        val dark = dark{{THEME_PREFIX}}Colors()

        assertTrue(light.isLight)
        assertFalse(dark.isLight)
        assertEquals(light.primary, dark.primary)
        assertNotEquals(light.surface, dark.surface)
    }

    @Test
    fun `no role is left unspecified`() {
        listOf(light{{THEME_PREFIX}}Colors(), dark{{THEME_PREFIX}}Colors()).forEach { colors ->
            val unspecified = colors.javaClass.declaredFields
                .filter { it.type == Long::class.javaPrimitiveType }
                .onEach { it.isAccessible = true }
                .filter { it.getLong(colors).toULong() == Color.Unspecified.value }
                .map { it.name }
            assertEquals(emptyList<String>(), unspecified)
        }
    }
}
