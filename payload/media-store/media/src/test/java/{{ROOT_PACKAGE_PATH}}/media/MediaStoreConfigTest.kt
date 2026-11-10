package {{ROOT_PACKAGE}}.media

import org.junit.Test

class MediaStoreConfigTest {

    @Test(expected = IllegalArgumentException::class)
    fun `a non-positive ttl is rejected - it would sweep captures the moment they are taken`() {
        MediaStoreConfig(staleAfterMillis = 0)
    }

    @Test
    fun `a positive ttl is accepted`() {
        MediaStoreConfig(staleAfterMillis = 1)
    }
}
