package pw.x4.ninety.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NodeIdentityTest {
    private val base = Node(
        proto = "vless",
        name = "Node",
        host = "example.com",
        port = 443,
        uuid = "00000000-0000-0000-0000-000000000001",
        security = "reality",
        pbk = "public-key-a",
        sid = "01",
        subId = "profile-a",
    )

    @Test
    fun `same connection in different profiles has different instance id`() {
        assertNotEquals(base.id, base.copy(subId = "profile-b").id)
        assertEquals(base.fingerprint, base.copy(subId = "profile-b").fingerprint)
    }

    @Test
    fun `connection-affecting fields participate in fingerprint`() {
        assertNotEquals(base.id, base.copy(path = "/different").id)
        assertNotEquals(base.id, base.copy(pbk = "public-key-b").id)
        assertNotEquals(base.id, base.copy(type = "xhttp").id)
    }

    @Test
    fun `display name does not change stable identity`() {
        assertEquals(base.id, base.copy(name = "Renamed").id)
    }
}
