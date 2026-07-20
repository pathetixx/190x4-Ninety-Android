package pw.x4.ninety.core.quality

import kotlin.math.min

/** Tuning values are supplied by the platform layer and kept out of Android/libbox code. */
data class QualityPolicy(
    val historySize: Int = 12,
    val minSamplesForSwitch: Int = 2,
    val failureThreshold: Int = 2,
    val baseCooldownMs: Long = 60_000,
    val maxCooldownMs: Long = 15 * 60_000,
    val minDwellMs: Long = 90_000,
    val switchMargin: Int = 8,
    val staleAfterMs: Long = 30 * 60_000,
) {
    init {
        require(historySize in 3..64)
        require(minSamplesForSwitch in 1..historySize)
        require(failureThreshold in 1..10)
        require(baseCooldownMs > 0)
        require(maxCooldownMs >= baseCooldownMs)
        require(minDwellMs >= 0)
        require(switchMargin in 0..100)
        require(staleAfterMs > 0)
    }
}

data class QualitySample(
    val measuredAtMs: Long,
    val delayMs: Int?,
) {
    val successful: Boolean get() = delayMs != null && delayMs in 1 until UNAVAILABLE_DELAY

    companion object {
        const val UNAVAILABLE_DELAY = 65_000
    }
}

data class NodeQualityHistory(
    val samples: List<QualitySample> = emptyList(),
    val consecutiveFailures: Int = 0,
    val cooldownUntilMs: Long = 0,
)

data class QualityState(
    val nodes: Map<String, NodeQualityHistory> = emptyMap(),
    val recommendedNodeId: String? = null,
    val recommendationSinceMs: Long = 0,
    val updatedAtMs: Long = 0,
)

data class NodeQualityRating(
    val nodeId: String,
    val score: Int,
    val medianDelayMs: Int?,
    val jitterMs: Int,
    val successRate: Int,
    val sampleCount: Int,
    val successfulSamples: Int,
    val consecutiveFailures: Int,
    val cooldownUntilMs: Long,
    val available: Boolean,
    val stale: Boolean,
)

enum class QualityDecisionReason {
    INITIAL,
    KEEP,
    BETTER_SCORE,
    INCUMBENT_UNAVAILABLE,
    NO_AVAILABLE_NODE,
}

data class QualityDecision(
    val state: QualityState,
    val ratings: Map<String, NodeQualityRating>,
    val switched: Boolean,
    val reason: QualityDecisionReason,
)

/**
 * Stateful policy for Ninety Auto.
 *
 * libbox remains responsible for measurements. This engine only consumes a completed delay batch,
 * keeps bounded history and decides whether a challenger is sufficiently healthier to replace the
 * incumbent. All time is injected, so tests never sleep and results are deterministic.
 */
object QualityEngine {
    fun observe(
        state: QualityState,
        candidateNodeIds: Collection<String>,
        measurements: Map<String, Int?>,
        activeNodeId: String?,
        nowMs: Long,
        policy: QualityPolicy = QualityPolicy(),
        retainedNodeIds: Collection<String> = candidateNodeIds,
    ): QualityDecision {
        val candidates = candidateNodeIds.filter(String::isNotBlank).distinct().sorted()
        val retained = retainedNodeIds.filter(String::isNotBlank).toHashSet()
        val histories = state.nodes.filterKeys(retained::contains).toMutableMap()

        candidates.forEach { nodeId ->
            val previous = histories[nodeId] ?: NodeQualityHistory()
            val delay = measurements[nodeId]?.takeIf { it in 1 until QualitySample.UNAVAILABLE_DELAY }
            histories[nodeId] = append(previous, QualitySample(nowMs, delay), nowMs, policy)
        }

        val interim = state.copy(nodes = histories, updatedAtMs = nowMs)
        val ratings = rate(interim, candidates, nowMs, policy)
        val decision = choose(interim, ratings, candidates, activeNodeId, nowMs, policy)
        return decision.copy(ratings = ratings)
    }

    fun rate(
        state: QualityState,
        candidateNodeIds: Collection<String>,
        nowMs: Long,
        policy: QualityPolicy = QualityPolicy(),
    ): Map<String, NodeQualityRating> = candidateNodeIds
        .filter(String::isNotBlank)
        .distinct()
        .sorted()
        .associateWith { nodeId -> rating(nodeId, state.nodes[nodeId] ?: NodeQualityHistory(), nowMs, policy) }

    fun prune(state: QualityState, retainedNodeIds: Collection<String>): QualityState {
        val retained = retainedNodeIds.filter(String::isNotBlank).toHashSet()
        val nodes = state.nodes.filterKeys(retained::contains)
        val recommendation = state.recommendedNodeId?.takeIf(retained::contains)
        return state.copy(
            nodes = nodes,
            recommendedNodeId = recommendation,
            recommendationSinceMs = if (recommendation == null) 0 else state.recommendationSinceMs,
        )
    }

    private fun append(
        previous: NodeQualityHistory,
        sample: QualitySample,
        nowMs: Long,
        policy: QualityPolicy,
    ): NodeQualityHistory {
        val samples = (previous.samples + sample).takeLast(policy.historySize)
        if (sample.successful) {
            return NodeQualityHistory(samples = samples)
        }

        val failures = previous.consecutiveFailures + 1
        val cooldownUntil = if (failures >= policy.failureThreshold) {
            val exponent = (failures - policy.failureThreshold).coerceIn(0, 20)
            var duration = policy.baseCooldownMs
            repeat(exponent) {
                duration = min(policy.maxCooldownMs, duration * 2)
            }
            nowMs + duration
        } else {
            previous.cooldownUntilMs.takeIf { it > nowMs } ?: 0
        }
        return NodeQualityHistory(
            samples = samples,
            consecutiveFailures = failures,
            cooldownUntilMs = cooldownUntil,
        )
    }

    private fun rating(
        nodeId: String,
        history: NodeQualityHistory,
        nowMs: Long,
        policy: QualityPolicy,
    ): NodeQualityRating {
        val successes = history.samples.filter(QualitySample::successful)
        val delays = successes.mapNotNull(QualitySample::delayMs).sorted()
        val median = percentile(delays, 50)
        val p90 = percentile(delays, 90)
        val jitter = if (median == null || p90 == null) 0 else (p90 - median).coerceAtLeast(0)
        val successRate = if (history.samples.isEmpty()) 0 else successes.size * 100 / history.samples.size
        val lastSuccess = successes.maxOfOrNull(QualitySample::measuredAtMs)
        val stale = lastSuccess == null || nowMs - lastSuccess > policy.staleAfterMs
        val coolingDown = history.cooldownUntilMs > nowMs

        val score = if (median == null || stale || coolingDown) {
            0
        } else {
            val latencyPenalty = min(60, median / 25)
            val jitterPenalty = min(20, jitter / 40)
            val failurePenalty = min(35, (100 - successRate) * 35 / 100)
            (100 - latencyPenalty - jitterPenalty - failurePenalty).coerceIn(1, 100)
        }

        return NodeQualityRating(
            nodeId = nodeId,
            score = score,
            medianDelayMs = median,
            jitterMs = jitter,
            successRate = successRate,
            sampleCount = history.samples.size,
            successfulSamples = successes.size,
            consecutiveFailures = history.consecutiveFailures,
            cooldownUntilMs = history.cooldownUntilMs,
            available = median != null && !stale && !coolingDown,
            stale = stale,
        )
    }

    private fun choose(
        state: QualityState,
        ratings: Map<String, NodeQualityRating>,
        candidates: List<String>,
        activeNodeId: String?,
        nowMs: Long,
        policy: QualityPolicy,
    ): QualityDecision {
        if (candidates.isEmpty()) {
            return QualityDecision(
                state = state.copy(recommendedNodeId = null, recommendationSinceMs = 0),
                ratings = ratings,
                switched = state.recommendedNodeId != null,
                reason = QualityDecisionReason.NO_AVAILABLE_NODE,
            )
        }

        val best = ratings.values
            .filter(NodeQualityRating::available)
            .sortedWith(
                compareByDescending<NodeQualityRating> { it.score }
                    .thenBy { it.medianDelayMs ?: Int.MAX_VALUE }
                    .thenBy(NodeQualityRating::nodeId),
            )
            .firstOrNull()

        val incumbentId = state.recommendedNodeId?.takeIf(candidates::contains)
        if (incumbentId == null) {
            val anchor = activeNodeId
                ?.takeIf(candidates::contains)
                ?.takeIf { ratings[it]?.available == true }
                ?: best?.nodeId
                ?: activeNodeId?.takeIf(candidates::contains)
                ?: candidates.first()
            return QualityDecision(
                state = state.copy(recommendedNodeId = anchor, recommendationSinceMs = nowMs),
                ratings = ratings,
                switched = anchor != state.recommendedNodeId,
                reason = QualityDecisionReason.INITIAL,
            )
        }

        val incumbent = ratings.getValue(incumbentId)
        if (!incumbent.available) {
            val replacement = best?.nodeId
            if (replacement != null && replacement != incumbentId) {
                return QualityDecision(
                    state = state.copy(recommendedNodeId = replacement, recommendationSinceMs = nowMs),
                    ratings = ratings,
                    switched = true,
                    reason = QualityDecisionReason.INCUMBENT_UNAVAILABLE,
                )
            }
            return QualityDecision(state, ratings, switched = false, reason = QualityDecisionReason.NO_AVAILABLE_NODE)
        }

        val challenger = best
        val dwellElapsed = nowMs - state.recommendationSinceMs >= policy.minDwellMs
        val challengerReady = challenger != null && challenger.successfulSamples >= policy.minSamplesForSwitch
        val materiallyBetter = challenger != null && challenger.score >= incumbent.score + policy.switchMargin
        if (
            challenger != null && challenger.nodeId != incumbentId &&
            dwellElapsed && challengerReady && materiallyBetter
        ) {
            return QualityDecision(
                state = state.copy(recommendedNodeId = challenger.nodeId, recommendationSinceMs = nowMs),
                ratings = ratings,
                switched = true,
                reason = QualityDecisionReason.BETTER_SCORE,
            )
        }

        return QualityDecision(state, ratings, switched = false, reason = QualityDecisionReason.KEEP)
    }

    private fun percentile(sorted: List<Int>, percent: Int): Int? {
        if (sorted.isEmpty()) return null
        val index = ((sorted.lastIndex * percent) + 99) / 100
        return sorted[index.coerceIn(0, sorted.lastIndex)]
    }
}
