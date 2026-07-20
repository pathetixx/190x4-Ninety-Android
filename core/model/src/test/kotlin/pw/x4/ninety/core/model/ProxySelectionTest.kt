package pw.x4.ninety.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProxySelectionTest {
    @Test
    fun `null and blank persisted values mean no selection`() {
        assertNull(ProxySelection.fromPersisted(null))
        assertNull(ProxySelection.fromPersisted(""))
        assertNull(ProxySelection.fromPersisted("   "))
    }

    @Test
    fun `auto round trips through storage`() {
        val selection = ProxySelection.fromPersisted(ProxySelection.AUTO_ID)

        assertEquals(ProxySelection.Auto, selection)
        assertEquals(ProxySelection.AUTO_ID, selection?.persistedValue)
    }

    @Test
    fun `node round trips through storage`() {
        val selection = ProxySelection.fromPersisted("node-42")

        assertEquals(ProxySelection.Node("node-42"), selection)
        assertEquals("node-42", selection?.persistedValue)
    }

    @Test
    fun `reserved auto id cannot be used as a node id`() {
        assertFailsWith<IllegalArgumentException> {
            ProxySelection.Node(ProxySelection.AUTO_ID)
        }
    }
}
