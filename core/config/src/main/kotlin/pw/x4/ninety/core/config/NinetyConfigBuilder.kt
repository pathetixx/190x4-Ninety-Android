package pw.x4.ninety.core.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pw.x4.ninety.core.model.DomainMatch
import pw.x4.ninety.core.model.ProxySelection
import pw.x4.ninety.core.model.RoutingRule
import pw.x4.ninety.core.model.RoutingRuleAction
import pw.x4.ninety.core.model.RoutingRuleSanitizer
import pw.x4.ninety.core.model.RoutingRuleType

/**
 * Product-level config facade. The protocol/DNS base builder stays stable while optional advanced
 * features decorate the result in deterministic priority order.
 */
object NinetyConfigBuilder {
    private val json = Json { ignoreUnknownKeys = false }

    fun build(
        nodes: List<ConfigNode>,
        selection: ProxySelection? = ProxySelection.Auto,
        logPath: String? = null,
        options: SingBoxOptions = SingBoxOptions(),
    ): String {
        val base = SingBoxConfigBuilder.build(nodes, selection, logPath, options)
        val warpEndpoint = WarpEndpointBuilder.build(options.warp)
        val protectedOutbound = if (warpEndpoint == null) PROXY_TAG else WarpEndpointBuilder.TAG
        val custom = options.customRules.mapNotNull { it.toSingBox(options.routingPlatform, protectedOutbound) }
        if (custom.isEmpty() && warpEndpoint == null) return base

        val root = json.parseToJsonElement(base).jsonObject
        val route = decorateRoute(root.getValue("route").jsonObject, custom, warpEndpoint != null)
        val updated = root.toMutableMap().apply {
            put("route", route)
            if (warpEndpoint != null) {
                put("dns", decorateDns(root.getValue("dns").jsonObject))
                put("endpoints", JsonArray(listOf(warpEndpoint)))
            }
        }
        return JsonObject(updated).toString()
    }

    fun tagOfId(id: String): String = SingBoxConfigBuilder.tagOfId(id)

    private fun decorateRoute(
        route: JsonObject,
        custom: List<JsonObject>,
        warpEnabled: Boolean,
    ): JsonObject {
        val existingRules = route.getValue("rules").jsonArray
        val insertionIndex = existingRules.serviceRulePrefixLength()
        val mergedRules = buildList<JsonElement> {
            addAll(existingRules.take(insertionIndex))
            addAll(custom)
            addAll(existingRules.drop(insertionIndex))
        }
        return JsonObject(route.toMutableMap().apply {
            put("rules", JsonArray(mergedRules))
            if (warpEnabled) {
                put("final", JsonPrimitive(WarpEndpointBuilder.TAG))
                route["rule_set"]?.jsonArray?.let { sets ->
                    put("rule_set", JsonArray(sets.map { element ->
                        val set = element.jsonObject
                        JsonObject(set.toMutableMap().apply {
                            if (set.containsKey("download_detour")) {
                                put("download_detour", JsonPrimitive(WarpEndpointBuilder.TAG))
                            }
                        })
                    }))
                }
            }
        })
    }

    private fun decorateDns(dns: JsonObject): JsonObject = JsonObject(dns.toMutableMap().apply {
        dns["servers"]?.jsonArray?.let { servers ->
            put("servers", JsonArray(servers.map { element ->
                val server = element.jsonObject
                if (server["tag"]?.jsonPrimitive?.contentOrNull != "dns-remote") {
                    server
                } else {
                    JsonObject(server.toMutableMap().apply {
                        put("detour", JsonPrimitive(WarpEndpointBuilder.TAG))
                    })
                }
            }))
        }
    })

    private fun RoutingRule.toSingBox(platform: RoutingPlatform, protectedOutbound: String): JsonObject? {
        if (!enabled) return null
        val clean = RoutingRuleSanitizer.sanitize(this).rule
        if (clean.values.isEmpty()) return null

        val subject = when (clean.type) {
            RoutingRuleType.DOMAIN -> when (clean.match) {
                DomainMatch.SUFFIX -> "domain_suffix"
                DomainMatch.EXACT -> "domain"
                DomainMatch.KEYWORD -> "domain_keyword"
            }
            RoutingRuleType.IP -> "ip_cidr"
            RoutingRuleType.ANDROID_PACKAGE -> if (platform == RoutingPlatform.ANDROID) "package_name" else return null
            RoutingRuleType.PROCESS_NAME -> if (platform == RoutingPlatform.DESKTOP) "process_name" else return null
        }

        return buildJsonObject {
            put(subject, JsonArray(clean.values.map(::JsonPrimitive)))
            when (clean.action) {
                RoutingRuleAction.PROXY -> put("outbound", protectedOutbound)
                RoutingRuleAction.DIRECT -> put("outbound", "direct")
                RoutingRuleAction.BLOCK -> put("action", "reject")
            }
        }
    }

    /** sniff and DNS hijack must always remain above user terminal rules. */
    private fun JsonArray.serviceRulePrefixLength(): Int {
        var index = 0
        if (getOrNull(index)?.jsonObject?.get("action") == JsonPrimitive("sniff")) index++
        if (getOrNull(index)?.jsonObject?.get("action") == JsonPrimitive("hijack-dns")) index++
        return index
    }

    private const val PROXY_TAG = "proxy"
}
