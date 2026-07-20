package pw.x4.ninety.vpn

import pw.x4.ninety.core.quality.NodeQualityHistory
import pw.x4.ninety.core.quality.QualityState

internal data class QualityIdMigrationResult(
    val states: Map<String, QualityState>,
    val changed: Boolean,
)

internal fun migrateQualityNodeIds(
    source: Map<String, QualityState>,
    aliasesByProfile: Map<String, Map<String, String>>,
): QualityIdMigrationResult {
    var changed = false
    val migrated = source.mapValues { (profileId, state) ->
        val aliases = aliasesByProfile[profileId].orEmpty()
        if (aliases.isEmpty()) return@mapValues state

        val nodes = linkedMapOf<String, NodeQualityHistory>()
        state.nodes.forEach { (storedId, history) ->
            val targetId = aliases[storedId] ?: storedId
            if (targetId != storedId) changed = true
            nodes[targetId] = nodes[targetId]?.merge(history) ?: history
        }
        val recommendation = state.recommendedNodeId?.let { stored -> aliases[stored] ?: stored }
        if (recommendation != state.recommendedNodeId) changed = true
        state.copy(nodes = nodes, recommendedNodeId = recommendation)
    }
    return QualityIdMigrationResult(migrated, changed)
}

private fun NodeQualityHistory.merge(other: NodeQualityHistory): NodeQualityHistory =
    NodeQualityHistory(
        samples = (samples + other.samples)
            .distinctBy { it.measuredAtMs to it.delayMs }
            .sortedBy { it.measuredAtMs },
        consecutiveFailures = maxOf(consecutiveFailures, other.consecutiveFailures),
        cooldownUntilMs = maxOf(cooldownUntilMs, other.cooldownUntilMs),
    )
