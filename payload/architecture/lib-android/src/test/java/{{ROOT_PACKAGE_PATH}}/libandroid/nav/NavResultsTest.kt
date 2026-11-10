package {{ROOT_PACKAGE}}.libandroid.nav

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavResultsTest {

    private val key = NavResultKey<String>("test.result.id")

    @Test
    fun `a written result is visible on the reader's flow`() {
        val handle = SavedStateHandle()
        val flow = handle.resultFlow(key)

        handle.putResult(key, "item-1")

        assertEquals("item-1", flow.value)
    }

    @Test
    fun `clearing keeps the same flow alive for the next result`() {
        val handle = SavedStateHandle()
        val flow = handle.resultFlow(key)

        handle.putResult(key, "first")
        handle.clearResult(key)
        assertNull(flow.value)

        handle.putResult(key, "second")
        assertEquals("second", flow.value)
    }
}
