package pw.x4.ninety.core.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QualityEngineTest {
    private val policy = QualityPolicy(
        historySize = 4,
        minSamplesForSwitch = 2,
        failureThreshold = 2,
        baseCooldownMs = 1_000,
        maxCooldownMs = 8_000,
        minDwellMs = 5_000,
        switchMargin = 8,
        staleAfterMs = 60_000,
    )

    @Test
    fun `first observation anchors current libbox node`() {
        val decision = QualityEngine.observe(
            state = QualityState(),
            candidateNodeIds = listOf("a", "b"),
            measurements = mapOf("a" to 300, "b" to 40),
            activeNodeId = "a",
            nowMs = 1_000,
            policy = policy,
        )

        assertEquals("a", decision.state.recommendedNodeId)
        assertEquals(QualityDecisionReason.INITIAL, decision.reason)
    }

    @Test
    fun `challenger cannot switch before dwell or enough samples`() {
        val first = QualityEngine.observe(
            state = QualityState(),
            candidateNodeIds = listOf("a", "b"),
            measurements = mapOf("a" to 300, "b" to 40),
            activeNodeId = "a",
            nowMs = 1_000,
            policy = policy,
        )
        val second = QualityEngine.observe(
            state = first.state,
            candidateNodeIds = listOf("a", "b"),
            measurements = mapOf("a" to 320, "b" to 45),
            activeNodeId = "a",
            nowMs = 2_000,
            policy = policy,
        )

        assertEquals("a", second.state.recommendedNodeId)
        assertFalse(second.switched)
        assertEquals(QualityDecisionReason.KEEP, second.reason)
    }

    @Test
    fun `materially healthier challenger switches after dwell`() {
        var state = QualityEngine.observe(
            state = QualityState(),
            candidateNodeIds = listOf("a", "b"),
            measurements = mapOf("a" to 350, "b" to 40),
            activeNodeId = "a",
            nowMs = 1_000,
            policy = policy,
        ).state
        state = QualityEngine.observe(
            state = state,
            candidateNodeIds = listOf("a", "b"),
            measurements = mapOf("a" to 340, "b" to 45),
            activeNodeId = "a",
            nowMs = 2_000,
            policy = policy,
        ).state
        val decision = QualityEngine.observe(
            state = state,
            candidateNodeIds = listOf("a", "b"),
            measurements = mapOf("a" to 360, "b" to 42),
            activeNodeId = "a",
            nowMs = 6_100,
            policy = policy,
        )

        assertEquals("b", decision.state.recommendedNodeId)
        assertTrue(decision.switched)
        assertEquals(QualityDecisionReason.BETTER_SCORE, decision.reason)
    }

    @Test
    fun `consecutive failures put incumbent into exponential cooldown`() {
        var state = QualityEngine.observe(
            state = QualityState(),
            candidateNodeIds = listOf("a", "b"),
            measurements = mapOf("a" to 80, "b" to 120),
            activeNodeId = "a",
            nowMs = 1_000,
            policy = policy,
        ).state
        state = QualityEngine.observe(
            state = state,
            candidateNodeIds = listOf("a", "b"),
            measurements = mapOf("a" to 0, "b" to 110),
            activeNodeId = "a",
            nowMs = 2_000,
            policy = policy,
        ).state
        val secondFailure = QualityEngine.observe(
            state = state,
            candidateNodeIds = listOf("a", "b"),
            measurements = mapOf("a" to 65_000, "b" to 100),
            activeNodeId = "a",
            nowMs = 3_000,
            policy = policy,
        )

        assertEquals("b", secondFailure.state.recommendedNodeId)
        assertEquals(4_000, secondFailure.state.nodes.getValue("a").cooldownUntilMs)
        assertEquals(QualityDecisionReason.INCUMBENT_UNAVAILABLE, secondFailure.reason)

        val thirdFailure = QualityEngine.observe(
            state = secondFailure.state,
            candidateNodeIds = listOf("a", "b"),
            measurements = mapOf("a" to null, "b" to 100),
            activeNodeId = "b",
            nowMs = 3_500,
            policy = policy,
        )
        assertEquals(5_500, thirdFailure.state.nodes.getValue("a").cooldownUntilMs)
    }

    @Test
    fun `history is bounded and success clears failure cooldown`() {
        var state = QualityState()
        repeat(6) { index ->
            state = QualityEngine.observe(
                state = state,
                candidateNodeIds = listOf("a"),
                measurements = mapOf("a" to if (index < 2) null else 100 + index),
                activeNodeId = "a",
                nowMs = index * 1_000L,
                policy = policy,
            ).state
        }

        val history = state.nodes.getValue("a")
        assertEquals(4, history.samples.size)
        assertEquals(0, history.consecutiveFailures)
        assertEquals(0, history.cooldownUntilMs)
    }

    @Test
    fun `ties are deterministic by node id`() {
        val decision = QualityEngine.observe(
            state = QualityState(),
            candidateNodeIds = listOf("z", "a"),
            measurements = mapOf("z" to 100, "a" to 100),
            activeNodeId = null,
            nowMs = 1_000,
            policy = policy,
        )

        assertEquals("a", decision.state.recommendedNodeId)
    }
}
