package pw.x4.ninety.data.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import pw.x4.ninety.core.model.ProxySelection

class StorageSnapshotTest {
    @Test
    fun `normalization removes orphan nodes and repairs active selection`() {
        val snapshot = StorageSnapshot(
            profiles = listOf(
                PersistedProfile("b", "B", "single"),
                PersistedProfile("a", "A", "sub"),
                PersistedProfile("a", "duplicate", "sub"),
            ),
            nodes = listOf(
                node("node-b", "b"),
                node("node-a", "a"),
                node("orphan", "missing"),
                node("node-a", "a"),
            ),
            preferences = PreferenceSnapshot(
                activeProfileId = "missing",
                activeNodeId = "missing-node",
            ),
        ).normalized()

        assertEquals(listOf("a", "b"), snapshot.profiles.map { it.id })
        assertEquals(listOf("node-a", "node-b"), snapshot.nodes.map { it.id })
        assertEquals("a", snapshot.preferences.activeProfileId)
        assertEquals("node-a", snapshot.preferences.activeNodeId)
    }

    @Test
    fun `auto survives only when active profile has nodes`() {
        val populated = StorageSnapshot(
            profiles = listOf(PersistedProfile("p", "P", "sub")),
            nodes = listOf(node("n", "p")),
            preferences = PreferenceSnapshot(
                activeProfileId = "p",
                activeNodeId = ProxySelection.AUTO_ID,
            ),
        ).normalized()
        assertEquals(ProxySelection.AUTO_ID, populated.preferences.activeNodeId)

        val empty = StorageSnapshot(
            profiles = listOf(PersistedProfile("p", "P", "sub")),
            preferences = PreferenceSnapshot(
                activeProfileId = "p",
                activeNodeId = ProxySelection.AUTO_ID,
            ),
        ).normalized()
        assertNull(empty.preferences.activeNodeId)
    }

    private fun node(id: String, profileId: String) = PersistedNode(
        id = id,
        profileId = profileId,
        protocol = "vless",
        name = id,
        host = "example.com",
        port = 443,
    )
}
