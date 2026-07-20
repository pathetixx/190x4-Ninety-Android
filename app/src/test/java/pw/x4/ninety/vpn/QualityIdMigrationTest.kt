package pw.x4.ninety.vpn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pw.x4.ninety.core.quality.NodeQualityHistory
import pw.x4.ninety.core.quality.QualitySample
import pw.x4.ninety.core.quality.QualityState

class QualityIdMigrationTest {
    @Test
    fun `remaps recommendation and merges colliding histories`() {
        val oldA = NodeQualityHistory(
            samples = listOf(QualitySample(1, 100), QualitySample(2, 90)),
            consecutiveFailures = 1,
            cooldownUntilMs = 10,
        )
        val oldB = NodeQualityHistory(
            samples = listOf(QualitySample(3, 80)),
            consecutiveFailures = 3,
            cooldownUntilMs = 20,
        )
        val source = mapOf(
            "profile" to QualityState(
                nodes = mapOf("old-a" to oldA, "old-b" to oldB),
                recommendedNodeId = "old-b",
                recommendationSinceMs = 5,
                updatedAtMs = 6,
            ),
        )

        val result = migrateQualityNodeIds(
            source = source,
            aliasesByProfile = mapOf(
                "profile" to mapOf("old-a" to "new", "old-b" to "new"),
            ),
        )

        assertTrue(result.changed)
        val migrated = result.states.getValue("profile")
        assertEquals("new", migrated.recommendedNodeId)
        assertEquals(setOf("new"), migrated.nodes.keys)
        assertEquals(listOf(1L, 2L, 3L), migrated.nodes.getValue("new").samples.map { it.measuredAtMs })
        assertEquals(3, migrated.nodes.getValue("new").consecutiveFailures)
        assertEquals(20, migrated.nodes.getValue("new").cooldownUntilMs)
    }

    @Test
    fun `leaves current ids unchanged`() {
        val source = mapOf(
            "profile" to QualityState(
                nodes = mapOf("current" to NodeQualityHistory()),
                recommendedNodeId = "current",
            ),
        )

        val result = migrateQualityNodeIds(source, emptyMap())

        assertFalse(result.changed)
        assertEquals(source, result.states)
    }
}
