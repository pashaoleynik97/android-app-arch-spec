package {{ROOT_PACKAGE}}.network

import org.junit.Assert.assertEquals
import org.junit.Test
import {{ROOT_PACKAGE}}.network.config.NetworkConfig

class NetworkConfigTest {

    @Test(expected = IllegalArgumentException::class)
    fun `a base url without a trailing slash is refused at startup, not at the first request`() {
        NetworkConfig("https://api.example.test/v2")
    }

    @Test
    fun `a base url with a trailing slash is accepted`() {
        assertEquals("https://api.example.test/", NetworkConfig("https://api.example.test/").baseUrl)
    }

    @Test
    fun `the rendered build-type url is a valid config`() {
        NetworkConfig(BuildConfig.BASE_URL)
    }
}
