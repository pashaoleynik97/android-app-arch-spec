package {{ROOT_PACKAGE}}.libcommon.paging

import org.junit.Assert.assertEquals
import org.junit.Test

class PagingConfigTest {

    @Test
    fun `defaults match the example contract`() {
        with(PagingConfig()) {
            assertEquals(30, pageSize)
            assertEquals(10, prefetchDistance)
            // The example list endpoints are 1-based; a 0-based default would silently skip the first page.
            assertEquals(1, firstPage)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a non-positive page size is rejected`() {
        PagingConfig(pageSize = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative prefetch distance is rejected`() {
        PagingConfig(prefetchDistance = -1)
    }

    @Test
    fun `a zero prefetch distance is legal - append only at the very last row`() {
        assertEquals(0, PagingConfig(prefetchDistance = 0).prefetchDistance)
    }
}
