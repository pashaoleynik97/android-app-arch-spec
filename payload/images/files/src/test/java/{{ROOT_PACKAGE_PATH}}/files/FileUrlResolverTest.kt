package {{ROOT_PACKAGE}}.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import {{ROOT_PACKAGE}}.files.config.FilesConfig

class FileUrlResolverTest {

    private fun resolver(baseUrl: String, filesPath: String = FilesConfig.DEFAULT_FILES_PATH) =
        FileUrlResolver(FilesConfig(baseUrl, filesPath))

    @Test
    fun `joins host, files path and name`() {
        assertEquals("https://api.example.invalid/v1/files/u42-mini.jpg", resolver("https://api.example.invalid/").fileUrl("u42-mini.jpg"))
    }

    @Test
    fun `tolerates missing or doubled slashes on every side`() {
        val expected = "https://cdn.example.invalid/media/a.jpg"
        assertEquals(expected, resolver("https://cdn.example.invalid", "media").fileUrl("a.jpg"))
        assertEquals(expected, resolver("https://cdn.example.invalid/", "/media/").fileUrl("/a.jpg"))
    }

    @Test
    fun `encodes characters that are not safe in a path but keeps nested folders`() {
        assertEquals(
            "https://h/v1/files/team%20photos/a%2Bb.jpg",
            resolver("https://h/").fileUrl("team photos/a+b.jpg"),
        )
    }

    @Test
    fun `an optional name resolves only when present`() {
        val r = resolver("https://h/")
        assertEquals("https://h/v1/files/a.jpg", r.fileUrlOrNull("a.jpg"))
        assertNull(r.fileUrlOrNull(null))
        assertNull(r.fileUrlOrNull(""))
        assertNull(r.fileUrlOrNull("   "))
    }
}
