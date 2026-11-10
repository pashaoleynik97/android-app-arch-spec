package {{ROOT_PACKAGE}}.network.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpLogMaskingTest {

    @Test
    fun `a bearer header keeps its scheme and a short prefix only`() {
        val masked = HttpLogMasking.mask("Authorization: Bearer fake-access-123456789")

        assertEquals("Authorization: Bearer fake…(masked)", masked)
    }

    @Test
    fun `secret json fields are masked wherever they appear in a body`() {
        val body = """{"email":"user@example.test","password":"hunter22","data":{"accessToken":"abcdef123","refreshToken":"zyxw987"}}"""

        val masked = HttpLogMasking.mask(body)

        assertFalse(masked, masked.contains("hunter22"))
        assertFalse(masked, masked.contains("abcdef123"))
        assertFalse(masked, masked.contains("zyxw987"))
        assertTrue("non-secret fields stay readable", masked.contains("\"email\":\"user@example.test\""))
    }

    @Test
    fun `field names match case-insensitively and tolerate spaces around the colon`() {
        val masked = HttpLogMasking.mask("""{"PASSWORD" : "hunter22"}""")

        assertFalse(masked, masked.contains("hunter22"))
    }

    @Test
    fun `a line without secrets is returned unchanged`() {
        val line = "--> GET https://api.example.test/v1/items?page=2"

        assertEquals(line, HttpLogMasking.mask(line))
    }

    @Test
    fun `a cookie header is masked as a whole`() {
        val masked = HttpLogMasking.mask("Cookie: session=abcdef; theme=light")

        assertFalse(masked, masked.contains("abcdef"))
    }
}
