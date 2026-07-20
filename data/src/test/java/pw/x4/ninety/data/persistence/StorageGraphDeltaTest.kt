package pw.x4.ninety.data.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorageGraphDeltaTest {
    @Test
    fun `unchanged graph produces no mutations`() {
        val profile = PersistedProfile("p", "Profile", "sub")
        val node = node("n", "p")

        assertTrue(
            storageGraphDelta(
                currentNodes = listOf(node),
                currentProfiles = listOf(profile),
                expectedNodes = listOf(node),
                expectedProfiles = listOf(profile),
            ).isEmpty,
        )
    }

    @Test
    fun `changed graph deletes removed rows and upserts only differences`() {
        val currentProfile = PersistedProfile("p", "Old", "sub")
        val removedProfile = PersistedProfile("removed", "Removed", "sub")
        val currentNode = node("n", "p")
        val removedNode = node("removed-node", "removed")
        val updatedProfile = currentProfile.copy(name = "New")
        val updatedNode = currentNode.copy(name = "Updated")
        val addedNode = node("added", "p")

        val delta = storageGraphDelta(
            currentNodes = listOf(currentNode, removedNode),
            currentProfiles = listOf(currentProfile, removedProfile),
            expectedNodes = listOf(updatedNode, addedNode),
            expectedProfiles = listOf(updatedProfile),
        )

        assertEquals(listOf("removed-node"), delta.nodeIdsToDelete)
        assertEquals(listOf("removed"), delta.profileIdsToDelete)
        assertEquals(listOf(updatedProfile), delta.profilesToUpsert)
        assertEquals(listOf(addedNode, updatedNode), delta.nodesToUpsert)
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
