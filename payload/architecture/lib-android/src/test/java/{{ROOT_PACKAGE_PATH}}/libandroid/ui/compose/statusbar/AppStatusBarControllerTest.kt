package {{ROOT_PACKAGE}}.libandroid.ui.compose.statusbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppStatusBarControllerTest {

    @Test
    fun `the last shown config is the one rendered until hidden`() {
        val controller = AppStatusBarController()
        val first = AppStatusBarConfig(title = "First")
        val second = AppStatusBarConfig(title = "Second")

        controller.show(first)
        controller.show(second)
        assertEquals(second, controller.config)

        controller.hide()
        assertNull(controller.config)
    }
}
