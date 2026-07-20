package pw.x4.ninety.data.persistence

internal data class StorageGraphDelta(
    val nodeIdsToDelete: List<String>,
    val profileIdsToDelete: List<String>,
    val profilesToUpsert: List<PersistedProfile>,
    val nodesToUpsert: List<PersistedNode>,
) {
    val isEmpty: Boolean
        get() = nodeIdsToDelete.isEmpty() && profileIdsToDelete.isEmpty() &&
            profilesToUpsert.isEmpty() && nodesToUpsert.isEmpty()
}

internal fun storageGraphDelta(
    currentNodes: List<PersistedNode>,
    currentProfiles: List<PersistedProfile>,
    expectedNodes: List<PersistedNode>,
    expectedProfiles: List<PersistedProfile>,
): StorageGraphDelta {
    val currentNodesById = currentNodes.associateBy(PersistedNode::id)
    val expectedNodesById = expectedNodes.associateBy(PersistedNode::id)
    val currentProfilesById = currentProfiles.associateBy(PersistedProfile::id)
    val expectedProfilesById = expectedProfiles.associateBy(PersistedProfile::id)

    return StorageGraphDelta(
        nodeIdsToDelete = (currentNodesById.keys - expectedNodesById.keys).sorted(),
        profileIdsToDelete = (currentProfilesById.keys - expectedProfilesById.keys).sorted(),
        profilesToUpsert = expectedProfiles
            .filter { expected -> currentProfilesById[expected.id] != expected }
            .sortedBy(PersistedProfile::id),
        nodesToUpsert = expectedNodes
            .filter { expected -> currentNodesById[expected.id] != expected }
            .sortedBy(PersistedNode::id),
    )
}
