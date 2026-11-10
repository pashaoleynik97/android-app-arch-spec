package {{ROOT_PACKAGE}}.images.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {

    @Test
    fun `first letters of the first and last words, upper-cased`() {
        assertEquals("AL", Initials.of("ada lovelace"))
        assertEquals("AL", Initials.of("Ada King Lovelace"))
    }

    @Test
    fun `extra whitespace does not produce empty words`() {
        assertEquals("AL", Initials.of("  Ada \t  Lovelace  "))
    }

    @Test
    fun `one word gives one letter, nothing gives nothing`() {
        assertEquals("A", Initials.of("Ada"))
        assertEquals("", Initials.of(""))
        assertEquals("", Initials.of("   "))
        assertEquals("", Initials.of(null))
    }

    @Test
    fun `letters outside ASCII and surrogate pairs stay whole`() {
        assertEquals("ÉZ", Initials.of("émile zola"))
        assertEquals("😀B", Initials.of("😀 bob"))
    }

    @Test
    fun `the letter count is capped`() {
        assertEquals("A", Initials.of("Ada Lovelace", maxLetters = 1))
    }
}
