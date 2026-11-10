package {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSafeLineBreakTest {

    @Test
    fun `a single line has no breaks to check`() {
        assertTrue(breaksOnlyBetweenWords("Continue", emptyList()))
    }

    @Test
    fun `a break at a space is between words`() {
        // "Save changes" broken after "Save " — line 0 ends at offset 5.
        assertTrue(breaksOnlyBetweenWords("Save changes", listOf(5)))
    }

    @Test
    fun `a break before a space is between words`() {
        assertTrue(breaksOnlyBetweenWords("Save changes", listOf(4)))
    }

    @Test
    fun `a break after a hyphen is between words`() {
        assertTrue(breaksOnlyBetweenWords("Sign-in", listOf(5)))
    }

    @Test
    fun `a break inside a word splits it`() {
        assertFalse(breaksOnlyBetweenWords("Configuration", listOf(6)))
    }

    @Test
    fun `one bad break among good ones fails the layout`() {
        assertFalse(breaksOnlyBetweenWords("Open the configuration", listOf(5, 15)))
    }
}
