package {{ROOT_PACKAGE}}.libcommon.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopesTest {

    private data class DraftScope(val text: String = "") : Scopes.Scope

    private object DraftKey : Scopes.ScopeKey<DraftScope> {
        override val id = "draft"
        override fun create() = DraftScope()
    }

    private val scopes = Scopes()

    @Test
    fun `peek never creates a scope`() {
        assertNull(scopes.peekScope(DraftKey))
        assertFalse(scopes.isOpen(DraftKey))
    }

    @Test
    fun `getOrOpen creates lazily and then returns the same scope`() {
        val first = scopes.getOrOpenScope(DraftKey)
        scopes.updateScope(DraftKey, first.copy(text = "hello"))

        assertEquals("hello", scopes.getOrOpenScope(DraftKey).text)
        assertTrue(scopes.isOpen(DraftKey))
    }

    @Test
    fun `consume hands the scope over exactly once`() {
        scopes.updateScope(DraftKey, DraftScope("handoff"))

        assertEquals("handoff", scopes.consumeScope(DraftKey)?.text)
        assertNull(scopes.consumeScope(DraftKey))
    }

    @Test
    fun `close and closeAll drop scopes`() {
        scopes.updateScope(DraftKey, DraftScope("x"))
        scopes.closeScope(DraftKey)
        assertFalse(scopes.isOpen(DraftKey))

        scopes.updateScope(DraftKey, DraftScope("y"))
        scopes.closeAll()
        assertFalse(scopes.isOpen(DraftKey))
    }
}
