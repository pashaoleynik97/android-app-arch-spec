package {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AppSnackbarControllerTest {

    private val controller = AppSnackbarController()

    @Test
    fun `a screen withdraws its own banner`() {
        val own = AppSnackbarRequest(TopSnackbarData("Saved"))
        controller.show(own)

        controller.hide(own)

        assertNull(controller.request)
    }

    @Test
    fun `a leaving screen cannot withdraw the banner the arriving screen raised`() {
        val leaving = AppSnackbarRequest(TopSnackbarData("Old"))
        val arriving = AppSnackbarRequest(TopSnackbarData("New"))
        controller.show(leaving)
        controller.show(arriving)

        controller.hide(leaving)

        assertSame(arriving, controller.request)
    }

    @Test
    fun `hiding nothing changes nothing`() {
        val shown = AppSnackbarRequest(TopSnackbarData("Shown"))
        controller.show(shown)

        controller.hide(null)

        assertSame(shown, controller.request)
    }

    @Test
    fun `the host can clear any banner`() {
        controller.show(AppSnackbarRequest(TopSnackbarData("Anything")))
        controller.clear()
        assertNull(controller.request)
    }
}
