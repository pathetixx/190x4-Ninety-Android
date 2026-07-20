package pw.x4.ninety.vpn

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import pw.x4.ninety.core.model.ProxySelection
import pw.x4.ninety.core.quality.NodeQualityHistory
import pw.x4.ninety.core.quality.NodeQualityRating
import pw.x4.ninety.core.quality.QualityDecisionReason
import pw.x4.ninety.core.quality.QualityEngine
import pw.x4.ninety.core.quality.QualityPolicy
import pw.x4.ninety.core.quality.QualitySample
import pw.x4.ninety.core.quality.QualityState
import pw.x4.ninety.data.Options
import pw.x4.ninety.data.Prefs
import pw.x4.ninety.data.Store

/**
 * Android coordinator around the pure quality policy.
 *
 * libbox continues to measure every outbound through its urltest group. When the user selected
 * Auto, this coordinator turns the stable recommendation into a concrete selector default on the
 * next config build. A reload is requested only when the recommendation actually differs from the
 * node currently applied by Ninety.
 */
object QualityRuntime {
    data class Snapshot(
        val profileId: String? = null,
        val recommendedNodeId: String? = null,
        val ratings: Map<String, NodeQualityRating> = emptyMap(),
        val reason: QualityDecisionReason? = null,
        val updatedAtMs: Long = 0,
    )

    var snapshot by mutableStateOf(Snapshot())
        private set

    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private val states = linkedMapOf<String, QualityState>()
    private lateinit var context: Context
    private lateinit var prefs: Prefs

    @Volatile private var initialized = false
    @Volatile private var appliedRecommendationId: String? = null
    private var lastRecordedAtMs = 0L
    private var lastReloadAtMs = 0L

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            this.context = context.applicationContext
            prefs = Prefs.get(this.context)
            states.putAll(parse(prefs.qualityJson))
            pruneLocked()
            initialized = true
        }
        publishCurrent()
    }

    /** Called by ConfigBuilder on the serialized VPN worker before each start/reload. */
    fun selectionForConfig(
        persistedSelection: String?,
        candidateNodeIds: Collection<String>,
        options: Options.Data,
    ): ProxySelection? {
        val requested = ProxySelection.fromPersisted(persistedSelection)
        if (requested != ProxySelection.Auto || !options.qualityEnabled) {
            appliedRecommendationId = null
            return requested
        }

        val profileId = Store.activeProfileId
        val recommendation = synchronized(lock) {
            profileId
                ?.let(states::get)
                ?.recommendedNodeId
                ?.takeIf(candidateNodeIds::contains)
        }
        appliedRecommendationId = recommendation
        return recommendation?.let(ProxySelection::Node) ?: ProxySelection.Auto
    }

    /** Completed urltest batch. Invalid/absent delays are recorded as failures. */
    fun record(
        candidateNodeIds: Collection<String>,
        delaysByNodeId: Map<String, Int?>,
        rawAutoNodeId: String?,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (!initialized || !Options.data.qualityEnabled) return
        if (nowMs - lastRecordedAtMs < MIN_BATCH_INTERVAL_MS) return

        val profileId = Store.activeProfileId ?: return
        val candidates = candidateNodeIds.filter(String::isNotBlank).distinct()
        if (candidates.isEmpty()) return
        val knownNodeIds = Store.nodesOf(profileId).map { it.id }
        val decision = synchronized(lock) {
            lastRecordedAtMs = nowMs
            val current = states[profileId] ?: QualityState()
            QualityEngine.observe(
                state = current,
                candidateNodeIds = candidates,
                measurements = delaysByNodeId,
                activeNodeId = rawAutoNodeId,
                nowMs = nowMs,
                policy = policy(Options.data),
                retainedNodeIds = knownNodeIds,
            ).also {
                states[profileId] = it.state
                pruneLocked()
                prefs.qualityJson = serialize(states)
            }
        }

        snapshot = Snapshot(
            profileId = profileId,
            recommendedNodeId = decision.state.recommendedNodeId,
            ratings = decision.ratings,
            reason = decision.reason,
            updatedAtMs = decision.state.updatedAtMs,
        )
        maybeApplyRecommendation(decision.state.recommendedNodeId, nowMs)
    }

    fun rating(nodeId: String): NodeQualityRating? = snapshot.ratings[nodeId]

    fun recommendedNodeId(profileId: String? = Store.activeProfileId): String? = synchronized(lock) {
        profileId?.let(states::get)?.recommendedNodeId
    }

    fun markTunnelStopped() {
        appliedRecommendationId = null
    }

    /** Used after profile/node mutations so removed entries do not survive forever. */
    fun prune() {
        if (!initialized) return
        synchronized(lock) {
            pruneLocked()
            prefs.qualityJson = serialize(states)
        }
        publishCurrent()
    }

    private fun maybeApplyRecommendation(targetNodeId: String?, nowMs: Long) {
        if (targetNodeId == null || !Store.isAutoActive || VpnController.state != ConnState.Connected) return
        if (targetNodeId == appliedRecommendationId) return
        if (nowMs - lastReloadAtMs < MIN_RELOAD_INTERVAL_MS) return

        lastReloadAtMs = nowMs
        main.post {
            if (
                initialized && Store.isAutoActive && VpnController.state == ConnState.Connected &&
                targetNodeId != appliedRecommendationId
            ) {
                NinetyVpnService.reload(context)
            }
        }
    }

    private fun publishCurrent() {
        if (!initialized) return
        val now = System.currentTimeMillis()
        val profileId = Store.activeProfileId
        val state = synchronized(lock) { profileId?.let(states::get) }
        val candidates = Store.supportedActiveNodes().map { it.id }
        snapshot = Snapshot(
            profileId = profileId,
            recommendedNodeId = state?.recommendedNodeId?.takeIf(candidates::contains),
            ratings = state?.let { QualityEngine.rate(it, candidates, now, policy(Options.data)) }.orEmpty(),
            updatedAtMs = state?.updatedAtMs ?: 0,
        )
    }

    private fun pruneLocked() {
        val profiles = Store.profiles.associate { profile ->
            profile.id to Store.nodesOf(profile.id).map { it.id }
        }
        states.keys.retainAll(profiles.keys)
        profiles.forEach { (profileId, nodeIds) ->
            states[profileId]?.let { states[profileId] = QualityEngine.prune(it, nodeIds) }
        }
    }

    private fun policy(options: Options.Data): QualityPolicy {
        val baseCooldown = options.qualityCooldownSec.coerceIn(15, 900) * 1_000L
        return QualityPolicy(
            historySize = HISTORY_SIZE,
            minSamplesForSwitch = MIN_SAMPLES_FOR_SWITCH,
            failureThreshold = options.qualityFailureThreshold.coerceIn(1, 5),
            baseCooldownMs = baseCooldown,
            maxCooldownMs = (baseCooldown * 16).coerceAtMost(60 * 60_000L),
            minDwellMs = options.qualityMinDwellSec.coerceIn(0, 3_600) * 1_000L,
            switchMargin = options.qualitySwitchMargin.coerceIn(0, 30),
            staleAfterMs = STALE_AFTER_MS,
        )
    }

    private fun serialize(source: Map<String, QualityState>): String = JSONObject().apply {
        put("version", FORMAT_VERSION)
        put("profiles", JSONArray().apply {
            source.toSortedMap().forEach { (profileId, state) ->
                put(JSONObject().apply {
                    put("id", profileId)
                    put("recommended", state.recommendedNodeId)
                    put("since", state.recommendationSinceMs)
                    put("updated", state.updatedAtMs)
                    put("nodes", JSONArray().apply {
                        state.nodes.toSortedMap().forEach { (nodeId, history) ->
                            put(JSONObject().apply {
                                put("id", nodeId)
                                put("failures", history.consecutiveFailures)
                                put("cooldown", history.cooldownUntilMs)
                                put("samples", JSONArray().apply {
                                    history.samples.takeLast(HISTORY_SIZE).forEach { sample ->
                                        put(JSONObject().apply {
                                            put("at", sample.measuredAtMs)
                                            if (sample.delayMs == null) put("delay", JSONObject.NULL)
                                            else put("delay", sample.delayMs)
                                        })
                                    }
                                })
                            })
                        }
                    })
                })
            }
        })
    }.toString()

    private fun parse(raw: String?): Map<String, QualityState> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("version") != FORMAT_VERSION) return@runCatching emptyMap()
            val profiles = root.optJSONArray("profiles") ?: return@runCatching emptyMap()
            buildMap {
                for (profileIndex in 0 until minOf(profiles.length(), MAX_PROFILES)) {
                    val profile = profiles.optJSONObject(profileIndex) ?: continue
                    val profileId = profile.optString("id").takeIf(String::isNotBlank) ?: continue
                    val nodesJson = profile.optJSONArray("nodes")
                    val nodes = buildMap {
                        if (nodesJson != null) {
                            for (nodeIndex in 0 until minOf(nodesJson.length(), MAX_NODES_PER_PROFILE)) {
                                val node = nodesJson.optJSONObject(nodeIndex) ?: continue
                                val nodeId = node.optString("id").takeIf(String::isNotBlank) ?: continue
                                val samplesJson = node.optJSONArray("samples")
                                val samples = buildList {
                                    if (samplesJson != null) {
                                        val start = (samplesJson.length() - HISTORY_SIZE).coerceAtLeast(0)
                                        for (sampleIndex in start until samplesJson.length()) {
                                            val sample = samplesJson.optJSONObject(sampleIndex) ?: continue
                                            val at = sample.optLong("at", -1)
                                            if (at < 0) continue
                                            val delay = if (sample.isNull("delay")) null else sample.optInt("delay")
                                            add(QualitySample(at, delay))
                                        }
                                    }
                                }
                                put(
                                    nodeId,
                                    NodeQualityHistory(
                                        samples = samples,
                                        consecutiveFailures = node.optInt("failures").coerceIn(0, 100),
                                        cooldownUntilMs = node.optLong("cooldown").coerceAtLeast(0),
                                    ),
                                )
                            }
                        }
                    }
                    put(
                        profileId,
                        QualityState(
                            nodes = nodes,
                            recommendedNodeId = profile.optString("recommended")
                                .takeIf(String::isNotBlank),
                            recommendationSinceMs = profile.optLong("since").coerceAtLeast(0),
                            updatedAtMs = profile.optLong("updated").coerceAtLeast(0),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private const val FORMAT_VERSION = 1
    private const val HISTORY_SIZE = 12
    private const val MIN_SAMPLES_FOR_SWITCH = 2
    private const val STALE_AFTER_MS = 30 * 60_000L
    private const val MIN_BATCH_INTERVAL_MS = 1_000L
    private const val MIN_RELOAD_INTERVAL_MS = 5_000L
    private const val MAX_PROFILES = 64
    private const val MAX_NODES_PER_PROFILE = 512
}
