package pw.x4.ninety.core.config

import java.net.URI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Minimal sing-box base used when WARP Direct is the only protected outbound.
 * It intentionally contains no selector/urltest group and therefore does not require proxy nodes.
 */
internal object DirectTunnelConfigBuilder {
    private const val GEO_BASE = "https://raw.githubusercontent.com/hiddify/hiddify-geo/rule-set"

    private val blockAdSets = listOf(
        "geosite-ads" to "$GEO_BASE/block/geosite-category-ads-all.srs",
        "geosite-malware" to "$GEO_BASE/block/geosite-malware.srs",
        "geosite-phishing" to "$GEO_BASE/block/geosite-phishing.srs",
        "geosite-cryptominers" to "$GEO_BASE/block/geosite-cryptominers.srs",
        "geoip-malware" to "$GEO_BASE/block/geoip-malware.srs",
        "geoip-phishing" to "$GEO_BASE/block/geoip-phishing.srs",
    )

    fun build(logPath: String?, options: SingBoxOptions): String = buildJsonObject {
        put("log", log(options, logPath))
        put("dns", dns(options))
        put("inbounds", buildJsonArray { add(tunInbound(options)) })
        put("outbounds", buildJsonArray {
            add(buildJsonObject {
                put("type", "direct")
                put("tag", "direct")
            })
        })
        put("route", route(options))
        put("experimental", buildJsonObject {
            put("cache_file", buildJsonObject {
                put("enabled", true)
                put("store_rdrc", true)
            })
            put("unified_delay", buildJsonObject { put("enabled", true) })
        })
    }.toString()

    private fun log(options: SingBoxOptions, logPath: String?): JsonObject = buildJsonObject {
        if (options.logDisabled) {
            put("disabled", true)
        } else {
            put("level", options.logLevel)
            put("timestamp", true)
            logPath?.let { put("output", it) }
        }
    }

    private fun tunInbound(options: SingBoxOptions): JsonObject = buildJsonObject {
        put("type", "tun")
        put("tag", "tun-in")
        put("address", buildJsonArray {
            add(JsonPrimitive("172.19.0.1/30"))
            if (options.ipv6Mode != Ipv6Mode.DISABLE) add(JsonPrimitive("fdfe:dcba:9876::1/126"))
        })
        put("mtu", options.mtu)
        put("auto_route", true)
        put("strict_route", options.strictRoute)
        put("stack", options.tunStack.wireName)
    }

    private fun dns(options: SingBoxOptions): JsonObject {
        val servers = mutableListOf<JsonObject>()
        servers += withTag(parseDnsAddress(options.dnsRemote), "dns-remote", detour = "direct")
        servers += withTag(parseDnsAddress(options.dnsDirect), "dns-direct")
        if (options.fakeDns) {
            servers += buildJsonObject {
                put("type", "fakeip")
                put("tag", "dns-fake")
                put("inet4_range", "198.18.0.0/15")
                put("inet6_range", "fc00::/18")
            }
        }
        return buildJsonObject {
            put("servers", JsonArray(servers))
            if (options.fakeDns) {
                put("rules", buildJsonArray {
                    add(buildJsonObject {
                        put("query_type", buildJsonArray {
                            add(JsonPrimitive("A")); add(JsonPrimitive("AAAA"))
                        })
                        put("server", "dns-fake")
                    })
                })
            }
            put("strategy", when (options.ipv6Mode) {
                Ipv6Mode.DISABLE -> "ipv4_only"
                Ipv6Mode.ENABLE -> "prefer_ipv4"
                Ipv6Mode.PREFER -> "prefer_ipv6"
                Ipv6Mode.ONLY -> "ipv6_only"
            })
            if (options.independentCache) put("independent_cache", true)
            put("final", "dns-remote")
        }
    }

    private fun route(options: SingBoxOptions): JsonObject {
        val rules = mutableListOf<JsonObject>()
        rules += buildJsonObject { put("action", "sniff") }
        rules += buildJsonObject {
            put("protocol", "dns")
            put("action", "hijack-dns")
        }
        val region = options.region.trim()
        if (region.isNotEmpty() && region != "other") {
            rules += buildJsonObject {
                put("domain_suffix", buildJsonArray { add(JsonPrimitive(".$region")) })
                put("outbound", "direct")
            }
            rules += buildJsonObject {
                put("rule_set", buildJsonArray {
                    add(JsonPrimitive("geosite-$region")); add(JsonPrimitive("geoip-$region"))
                })
                put("outbound", "direct")
            }
        }
        if (options.bypassLan) {
            rules += buildJsonObject {
                put("ip_is_private", true)
                put("outbound", "direct")
            }
        }
        if (options.blockAds) {
            rules += buildJsonObject {
                put("rule_set", JsonArray(blockAdSets.map { JsonPrimitive(it.first) }))
                put("action", "reject")
            }
        }

        val ruleSets = mutableListOf<JsonObject>()
        if (region.isNotEmpty() && region != "other") {
            ruleSets += remoteRuleSet("geoip-$region", "$GEO_BASE/country/geoip-$region.srs")
            ruleSets += remoteRuleSet("geosite-$region", "$GEO_BASE/country/geosite-$region.srs")
        }
        if (options.blockAds) blockAdSets.forEach { (tag, url) -> ruleSets += remoteRuleSet(tag, url) }

        return buildJsonObject {
            put("rules", JsonArray(rules))
            if (ruleSets.isNotEmpty()) put("rule_set", JsonArray(ruleSets))
            put("final", "direct")
            put("auto_detect_interface", true)
            put("default_domain_resolver", buildJsonObject { put("server", "dns-direct") })
        }
    }

    private fun remoteRuleSet(tag: String, url: String): JsonObject = buildJsonObject {
        put("type", "remote")
        put("tag", tag)
        put("format", "binary")
        put("url", url)
        put("update_interval", "120h")
        put("download_detour", "direct")
    }

    private fun withTag(base: JsonObject, tag: String, detour: String? = null): JsonObject = buildJsonObject {
        base.forEach { (key, value) -> put(key, value) }
        put("tag", tag)
        if (tag == "dns-remote") put("domain_resolver", "dns-direct")
        detour?.let { put("detour", it) }
    }

    private fun parseDnsAddress(raw: String): JsonObject {
        val value = raw.trim()
        if (value.isEmpty() || value == "local" || value == "system") {
            return buildJsonObject { put("type", "local") }
        }
        val uri = runCatching { URI(value) }.getOrNull()
        if (uri?.scheme.equals("https", ignoreCase = true) && !uri?.host.isNullOrBlank()) {
            return buildJsonObject {
                put("type", "https")
                put("server", requireNotNull(uri?.host).removeSurrounding("[", "]"))
                if (requireNotNull(uri).port >= 0) put("server_port", uri.port)
                uri.rawPath.orEmpty().takeIf { it.isNotEmpty() && it != "/" }?.let { put("path", it) }
            }
        }
        val match = Regex("^([a-zA-Z]+)://(.+)$").find(value)
        val type = match?.groupValues?.get(1)?.lowercase() ?: "udp"
        val source = match?.groupValues?.get(2) ?: value
        val (host, port) = splitHostPort(source)
        require(type in setOf("udp", "tcp", "tls", "quic")) { "unsupported DNS scheme: $type" }
        return buildJsonObject {
            put("type", type)
            put("server", host)
            port?.let { put("server_port", it) }
        }
    }

    private fun splitHostPort(value: String): Pair<String, Int?> {
        val source = value.trim()
        require(source.isNotEmpty() && source.none(Char::isWhitespace)) { "invalid DNS host: $value" }
        if (source.startsWith('[')) {
            val close = source.indexOf(']')
            require(close > 1) { "invalid bracketed DNS address: $value" }
            val host = source.substring(1, close)
            val suffix = source.substring(close + 1)
            return host to suffix.removePrefix(":").takeIf(String::isNotEmpty)?.toIntOrNull()
        }
        if (source.count { it == ':' } == 1) {
            val index = source.lastIndexOf(':')
            val port = source.substring(index + 1).toIntOrNull()
            if (port != null) return source.substring(0, index) to port
        }
        return source to null
    }
}
