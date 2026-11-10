package {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMenuControllerTest {

    private val controller = AppMenuController()
    private val item = AppMenuItem(title = "Settings", onClick = {})

    @Test
    fun `a screen without options has no menu to open`() {
        controller.open()
        assertFalse(controller.isOpen)
    }

    @Test
    fun `declared options can be opened and closed`() {
        controller.setItems(listOf(item))
        controller.open()
        assertTrue(controller.isOpen)

        controller.close()
        assertFalse(controller.isOpen)
    }

    @Test
    fun `losing all options closes an open menu`() {
        controller.setItems(listOf(item))
        controller.open()

        controller.setItems(emptyList())

        assertFalse(controller.isOpen)
    }

    @Test
    fun `reset closes the menu and forgets stale options`() {
        controller.setItems(listOf(item))
        controller.open()

        controller.reset()

        assertFalse(controller.isOpen)
        assertEquals(emptyList<AppMenuItem>(), controller.items)
    }
}
