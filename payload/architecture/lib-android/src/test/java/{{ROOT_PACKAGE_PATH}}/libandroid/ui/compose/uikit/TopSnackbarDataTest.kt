package {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the two app-wide banner rules for every call site at once. */
class TopSnackbarDataTest {

    @Test
    fun `a close button suppresses an explicit duration`() {
        assertNull(TopSnackbarData("Offline", showClose = true, durationMillis = 4_000L).autoDismissMillis)
    }

    @Test
    fun `a close button suppresses the default duration`() {
        assertNull(TopSnackbarData("Could not refresh", showClose = true).autoDismissMillis)
    }

    @Test
    fun `a banner without a close button keeps its duration`() {
        assertEquals(3_000L, TopSnackbarData("Back online", durationMillis = 3_000L).autoDismissMillis)
    }

    @Test
    fun `a banner without a close button defaults to the shared duration`() {
        assertEquals(TopSnackbarData.DEFAULT_DURATION_MILLIS, TopSnackbarData("Saved").autoDismissMillis)
    }

    @Test
    fun `a null duration persists until the owner clears it`() {
        assertNull(TopSnackbarData("Uploading", durationMillis = null).autoDismissMillis)
    }

    @Test
    fun `retry alone does not suppress auto-dismiss`() {
        assertEquals(5_000L, TopSnackbarData("Failed", showRetry = true, durationMillis = 5_000L).autoDismissMillis)
    }

    @Test
    fun `a close button as the only action makes the whole banner the close target`() {
        assertTrue(TopSnackbarData("Item removed", showClose = true).dismissesOnBodyTap)
    }

    @Test
    fun `a banner that also retries keeps the close button as its only close target`() {
        assertFalse(TopSnackbarData("Offline", showRetry = true, showClose = true).dismissesOnBodyTap)
    }

    @Test
    fun `a banner without a close button has no body tap dismissal`() {
        assertFalse(TopSnackbarData("Saved").dismissesOnBodyTap)
    }
}
