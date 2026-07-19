package pw.x4.ninety.core.config

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import pw.x4.ninety.core.model.ProxyNode
import pw.x4.ninety.core.model.ProxyProtocol
import pw.x4.ninety.core.model.ProxySelection

/**
 * Deterministic pure Kotlin builder for the Android/libbox sing-box 1.13 config.
 * No Android, persistence, UI or libbox APIs are referenced here.
 */
object SingBoxConfigBuilder {
    private const val GEO_BASE = "https://raw.githubusercontent.com/hiddify/hiddify-geo/rule-set"

    private val blockAdSets = listOf(
        "geosite-ads" to "$GEO_BASE/block/geosite-category-ads-all.srs",
        "geosite-malware" to "$GEO_BASE/block/geosite-malware.srs",
        "geosite-phishing" to "$GEO_BASE/block/geosite-phishing.srs",
        "geosite-cryptominers" to "$GEO_BASE/block/geosite-cryptominers.srs",
        "geoip-malware" to "$GEO_BASE/block/geoip-malware.srs",
        "geoip-phishing" to "$GEO_BASE/block/geoip-phishing.srs",
    )

    private val xhttpPassKeys = listOf(
        "host",
        "path",
        "headers",
        "xPaddingBytes",
        "noGRPCHeader",
        "noSSEHeader",
        "scMaxEachPostBytes",
        "scMinPostsIntervalMs",
        "scMaxBufferedPosts",
        "scStreamUpServerSecs",
        "xmux",
    )

    private val parserJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun build(
        nodes: List<ConfigNode>,
        selection: ProxySelection? = ProxySelection.Auto,
        logPath: String? = null,
        options: SingBoxOptions = SingBoxOptions(),
    ): String {
        require(nodes.isNotEmpty()) { "proxy node list must not be empty" }

        val uniqueNodes = nodes.distinctBy(ConfigNode::id)
        val nodeTags = uniqueNodes.map { tagOfId(it.id) }
        val selectedId = (selection as? ProxySelection.Node)?.nodeId
        val selectedTag = uniqueNodes
            .firstOrNull { it.id == selectedId }
            ?.let { tagOfId(it.id) }

        val outbounds = buildJsonArray {
            add(buildJsonObject {
                put("type", "selector")
                put("tag", "proxy")
                put("outbounds", buildJsonArray {
                    add(JsonPrimitive("auto"))
                    nodeTags.forEach { add(JsonPrimitive(it)) }
                })
                put("default", selectedTag ?: "auto")
                put("interrupt_exist_connections", true)
            })
            add(buildJsonObject {
                put("type", "urltest")
                put("tag", "auto")
                put("outbounds", stringArray(nodeTags))
                put("url", options.testUrl)
                put("interval", "${options.testIntervalSec}s")
                put("tolerance", 50)
                put("interrupt_exist_connections", false)
            })
            uniqueNodes.forEach { add(outbound(it.proxy, tagOfId(it.id), options)) }
            add(buildJsonObject {
                put("type", "direct")
                put("tag", "direct")
            })
        }

        return buildJsonObject {
            put("log", log(options, logPath))
            put("dns", dns(options))
            put("inbounds", buildJsonArray { add(tunInbound(options)) })
            put("outbounds", outbounds)
            put("route", route(options))
            put("experimental", experimental())
        }.toString()
    }

    fun tagOfId(id: String): String = "n$id"

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
            if (options.ipv6Mode != Ipv6Mode.DISABLE) {
                add(JsonPrimitive("fdfe:dcba:9876::1/126"))
            }
        })
        put("mtu", options.mtu)
        put("auto_route", true)
        put("strict_route", options.strictRoute)
        put("stack", options.tunStack.wireName)
    }

    private fun dns(options: SingBoxOptions): JsonObject {
        val remote = withFields(parseDnsAddress(options.dnsRemote)) {
            put("tag", "dns-remote")
            put("domain_resolver", "dns-direct")
            put("detour", "proxy")
        }
        val direct = withFields(parseDnsAddress(options.dnsDirect)) {
            put("tag", "dns-direct")
        }

        val servers = mutableListOf<JsonElement>(remote, direct)
        val rules = mutableListOf<JsonElement>()
        val region = options.region.trim()

        if (region.isNotEmpty() && region != "other") {
            rules += buildJsonObject {
                put("domain_suffix", stringArray(listOf(".$region")))
                put("server", "dns-direct")
                put("rewrite_ttl", 86400)
            }
            rules += buildJsonObject {
                put("rule_set", stringArray(listOf("geosite-$region")))
                put("server", "dns-direct")
                put("rewrite_ttl", 86400)
            }
        }

        if (options.fakeDns) {
            servers += buildJsonObject {
                put("tag", "dns-fake")
                put("type", "fakeip")
                put("inet4_range", "198.18.0.0/15")
                put("inet6_range", "fc00::/18")
            }
            rules += buildJsonObject {
                put("query_type", stringArray(listOf("A", "AAAA")))
                put("server", "dns-fake")
            }
        }

        return buildJsonObject {
            put("servers", JsonArray(servers))
            if (rules.isNotEmpty()) put("rules", JsonArray(rules))
            put("strategy", ipv6Strategy(options.ipv6Mode))
            if (options.independentCache) put("independent_cache", true)
            put("final", "dns-remote")
        }
    }

    private fun route(options: SingBoxOptions): JsonObject {
        val rules = mutableListOf<JsonElement>()
        rules += buildJsonObject { put("action", "sniff") }
        rules += buildJsonObject {
            put("protocol", "dns")
            put("action", "hijack-dns")
        }

        val region = options.region.trim()
        if (region.isNotEmpty() && region != "other") {
            rules += buildJsonObject {
                put("domain_suffix", stringArray(listOf(".$region")))
                put("outbound", "direct")
            }
            rules += buildJsonObject {
                put("rule_set", stringArray(listOf("geosite-$region", "geoip-$region")))
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
                put("rule_set", stringArray(blockAdSets.map { it.first }))
                put("action", "reject")
            }
        }

        val ruleSets = ruleSets(options)
        return buildJsonObject {
            put("rules", JsonArray(rules))
            if (ruleSets.isNotEmpty()) put("rule_set", JsonArray(ruleSets))
            put("final", "proxy")
            put("auto_detect_interface", true)
            put("default_domain_resolver", buildJsonObject { put("server", "dns-direct") })
        }
    }

    private fun ruleSets(options: SingBoxOptions): List<JsonElement> {
        val result = mutableListOf<JsonElement>()
        val region = options.region.trim()
        if (region.isNotEmpty() && region != "other") {
            result += remoteRuleSet("geoip-$region", "$GEO_BASE/country/geoip-$region.srs")
            result += remoteRuleSet("geosite-$region", "$GEO_BASE/country/geosite-$region.srs")
        }
        if (options.blockAds) {
            blockAdSets.forEach { (tag, url) -> result += remoteRuleSet(tag, url) }
        }
        return result
    }

    private fun remoteRuleSet(tag: String, url: String): JsonObject = buildJsonObject {
        put("type", "remote")
        put("tag", tag)
        put("format", "binary")
        put("url", url)
        put("update_interval", "120h")
        put("download_detour", "proxy")
    }

    private fun experimental(): JsonObject = buildJsonObject {
        put("cache_file", buildJsonObject {
            put("enabled", true)
            put("store_rdrc", true)
        })
        put("unified_delay", buildJsonObject { put("enabled", true) })
        put("monitoring", buildJsonObject {
            put("interval", "15m")
            put("idle_timeout", "45s")
        })
    }

    private fun outbound(node: ProxyNode, tag: String, options: SingBoxOptions): JsonObject = buildJsonObject {
        put("tag", tag)
        put("server", node.host)
        put("server_port", node.port)

        when (node.protocol) {
            ProxyProtocol.VLESS -> {
                put("type", "vless")
                put("uuid", node.uuid)
                put("packet_encoding", "xudp")
                if (node.flow.isNotEmpty()) put("flow", node.flow)
            }
            ProxyProtocol.VMESS -> {
                put("type", "vmess")
                put("uuid", node.uuid)
                put("security", node.cipher.ifBlank { "auto" })
                put("alter_id", node.alterId)
                put("packet_encoding", "xudp")
            }
            ProxyProtocol.TROJAN -> {
                put("type", "trojan")
                put("password", node.password)
            }
            ProxyProtocol.SHADOWSOCKS -> {
                put("type", "shadowsocks")
                put("method", node.method)
                put("password", node.password)
                if (node.plugin.isNotEmpty()) {
                    put("plugin", node.plugin)
                    if (node.pluginOptions.isNotEmpty()) put("plugin_opts", node.pluginOptions)
                }
            }
            ProxyProtocol.HYSTERIA2 -> {
                put("type", "hysteria2")
                put("password", node.password)
                if (node.upMbps > 0) put("up_mbps", node.upMbps)
                if (node.downMbps > 0) put("down_mbps", node.downMbps)
                if (node.obfs.isNotEmpty()) {
                    put("obfs", buildJsonObject {
                        put("type", node.obfs)
                        if (node.obfsPassword.isNotEmpty()) put("password", node.obfsPassword)
                    })
                }
                put("tls", quicTls(node))
            }
            ProxyProtocol.TUIC -> {
                put("type", "tuic")
                put("uuid", node.uuid)
                put("password", node.password)
                put("congestion_control", node.congestionControl.ifBlank { "bbr" })
                put("udp_relay_mode", node.udpRelayMode.ifBlank { "native" })
                put("zero_rtt_handshake", node.zeroRttHandshake)
                put("tls", quicTls(node))
            }
        }

        if (node.protocol !in setOf(ProxyProtocol.HYSTERIA2, ProxyProtocol.TUIC)) {
            tls(node, options)?.let { put("tls", it) }
            transport(node)?.let { put("transport", it) }
            if (options.muxEnable) put("multiplex", multiplex(options))
        }
    }

    private fun tls(node: ProxyNode, options: SingBoxOptions): JsonObject? {
        if (node.security != "tls" && node.security != "reality") return null

        return buildJsonObject {
            put("enabled", true)
            put("server_name", node.sni.ifBlank { node.host })
            put("utls", buildJsonObject {
                put("enabled", true)
                put("fingerprint", node.fingerprint.ifBlank { "chrome" })
            })
            if (node.alpn.isNotEmpty()) put("alpn", alpnArray(node.alpn))
            if (node.security == "reality") {
                put("reality", buildJsonObject {
                    put("enabled", true)
                    put("public_key", node.publicKey)
                    put("short_id", node.shortId)
                })
            }
            if (options.tlsFragment) {
                if (options.fragmentMode == FragmentMode.TCP) {
                    put("fragment", true)
                    if (options.fragmentFallbackDelay.isNotBlank()) {
                        put("fragment_fallback_delay", options.fragmentFallbackDelay)
                    }
                } else {
                    put("record_fragment", true)
                }
            }
            val tricks = buildJsonObject {
                if (options.tlsPadding) put("padding_size", "${options.paddingFrom}-${options.paddingTo}")
                if (options.mixedSniCase) put("mixedcase_sni", true)
            }
            if (tricks.isNotEmpty()) put("tls_tricks", tricks)
        }
    }

    private fun quicTls(node: ProxyNode): JsonObject = buildJsonObject {
        put("enabled", true)
        put("server_name", node.sni.ifBlank { node.host })
        put("insecure", node.insecure)
        if (node.disableSni) put("disable_sni", true)
        put("alpn", alpnArray(node.alpn.ifBlank { "h3" }))
        if (node.certificatePublicKeySha256.isNotEmpty()) {
            put("certificate_public_key_sha256", node.certificatePublicKeySha256)
        }
    }

    private fun multiplex(options: SingBoxOptions): JsonObject = buildJsonObject {
        put("enabled", true)
        put("protocol", options.muxProtocol)
        put("max_streams", options.muxMaxStreams)
        put("padding", options.muxPadding)
    }

    private fun transport(node: ProxyNode): JsonObject? = when (node.type) {
        "ws" -> buildJsonObject {
            put("type", "ws")
            if (node.path.isNotEmpty()) put("path", node.path)
            if (node.hostHeader.isNotEmpty()) {
                put("headers", buildJsonObject { put("Host", node.hostHeader) })
            }
        }
        "grpc" -> buildJsonObject {
            put("type", "grpc")
            if (node.serviceName.isNotEmpty()) put("service_name", node.serviceName)
        }
        "http", "h2" -> buildJsonObject {
            put("type", "http")
            if (node.path.isNotEmpty()) put("path", node.path)
            if (node.hostHeader.isNotEmpty()) {
                put("host", stringArray(node.hostHeader.split(',').map(String::trim).filter(String::isNotEmpty)))
            }
        }
        "xhttp" -> xhttpTransport(node)
        else -> null
    }

    private fun xhttpTransport(node: ProxyNode): JsonObject {
        val values = linkedMapOf<String, JsonElement>()
        values["type"] = JsonPrimitive("xhttp")
        if (node.path.isNotEmpty()) values["path"] = JsonPrimitive(node.path)
        if (node.hostHeader.isNotEmpty()) values["host"] = JsonPrimitive(node.hostHeader)
        if (node.mode.isNotEmpty()) values["mode"] = JsonPrimitive(node.mode)

        if (node.extra.isNotBlank()) {
            runCatching { parserJson.parseToJsonElement(node.extra) as? JsonObject }
                .getOrNull()
                ?.let { mergeXhttpExtra(values, it) }
        }

        if (values["mode"].primitiveContent().isNullOrEmpty()) {
            values["mode"] = JsonPrimitive("auto")
        }
        return JsonObject(values)
    }

    private fun mergeXhttpExtra(target: MutableMap<String, JsonElement>, extra: JsonObject) {
        xhttpPassKeys.forEach { key -> extra[key]?.let { target[key] = it } }
        extra.string("mode").takeIf(String::isNotEmpty)?.let { target["mode"] = JsonPrimitive(it) }
        (extra["downloadSettings"] as? JsonObject)
            ?.let(::xrayDownloadToSingBox)
            ?.let { target["downloadSettings"] = it }
    }

    private fun xrayDownloadToSingBox(settings: JsonObject): JsonObject? {
        val values = linkedMapOf<String, JsonElement>()
        settings.string("address").takeIf(String::isNotEmpty)?.let { values["server"] = JsonPrimitive(it) }
        settings.int("port")?.let { values["server_port"] = JsonPrimitive(it) }

        val xhttp = settings["xhttpSettings"] as? JsonObject ?: JsonObject(emptyMap())
        xhttpPassKeys.forEach { key -> xhttp[key]?.let { values[key] = it } }
        (xhttp["extra"] as? JsonObject)?.let { nested ->
            xhttpPassKeys.forEach { key -> nested[key]?.let { values[key] = it } }
        }
        xrayTlsToSingBox(settings)?.let { values["tls"] = it }
        return if (values.isEmpty()) null else JsonObject(values)
    }

    private fun xrayTlsToSingBox(settings: JsonObject): JsonObject? {
        val security = settings.string("security")
        if (security != "tls" && security != "reality") return null

        val tlsSettings = settings["tlsSettings"] as? JsonObject
        val realitySettings = settings["realitySettings"] as? JsonObject
        val source = tlsSettings ?: realitySettings ?: JsonObject(emptyMap())

        return buildJsonObject {
            put("enabled", true)
            source.string("serverName").ifBlank { source.string("server_name") }
                .takeIf(String::isNotEmpty)
                ?.let { put("server_name", it) }
            put("utls", buildJsonObject {
                put("enabled", true)
                put("fingerprint", source.string("fingerprint").ifBlank { "chrome" })
            })
            when (val alpn = source["alpn"]) {
                is JsonArray -> if (alpn.isNotEmpty()) put("alpn", alpn)
                is JsonPrimitive -> alpn.contentOrNull
                    ?.takeIf(String::isNotEmpty)
                    ?.let { put("alpn", alpnArray(it)) }
                else -> Unit
            }
            if (source.boolean("allowInsecure") || source.boolean("insecure")) put("insecure", true)
            if (security == "reality") {
                put("reality", buildJsonObject {
                    put("enabled", true)
                    put("public_key", source.string("publicKey").ifBlank { source.string("public_key") })
                    put("short_id", source.string("shortId").ifBlank { source.string("short_id") })
                })
            }
        }
    }

    private fun parseDnsAddress(raw: String): JsonObject {
        val value = raw.trim()
        if (value.isEmpty() || value == "local" || value == "system") {
            return buildJsonObject { put("type", "local") }
        }

        val match = Regex("^([a-zA-Z]+)://(.+)$").find(value)
        if (match == null) {
            require(!value.any(Char::isWhitespace) && !value.contains('/')) { "invalid DNS server address: $value" }
            return dnsServer("udp", splitOptionalHostPort(value))
        }

        val scheme = match.groupValues[1].lowercase()
        val rest = match.groupValues[2]
        return when (scheme) {
            "https" -> {
                val uri = runCatching { URI(value) }
                    .getOrElse { throw IllegalArgumentException("invalid DoH URL: $value", it) }
                val host = uri.host?.removeSurrounding("[", "]")
                require(uri.userInfo == null && !host.isNullOrBlank()) { "invalid DoH URL: $value" }
                buildJsonObject {
                    put("type", "https")
                    put("server", host)
                    if (uri.port >= 0) put("server_port", uri.port)
                    val path = uri.rawPath.orEmpty()
                    if (path.isNotEmpty() && path != "/") put("path", path)
                }
            }
            "tls", "tcp", "udp", "quic" -> dnsServer(scheme, splitOptionalHostPort(rest))
            else -> throw IllegalArgumentException("unsupported DNS scheme: $scheme")
        }
    }

    private fun dnsServer(type: String, hostPort: HostPort): JsonObject = buildJsonObject {
        put("type", type)
        put("server", hostPort.host)
        hostPort.port?.let { put("server_port", it) }
    }

    private fun splitOptionalHostPort(raw: String): HostPort {
        val value = raw.trim()
        require(value.isNotEmpty() && !value.any(Char::isWhitespace) && !value.contains('/')) {
            "invalid DNS host: $value"
        }

        if (value.startsWith('[')) {
            val close = value.indexOf(']')
            require(close > 1) { "invalid bracketed DNS address: $value" }
            val host = value.substring(1, close)
            val suffix = value.substring(close + 1)
            return when {
                suffix.isEmpty() -> HostPort(host, null)
                suffix.startsWith(':') -> HostPort(host, parsePort(suffix.substring(1)))
                else -> throw IllegalArgumentException("invalid DNS address suffix: $suffix")
            }
        }

        return if (value.count { it == ':' } == 1) {
            val separator = value.lastIndexOf(':')
            val host = value.substring(0, separator)
            require(host.isNotEmpty()) { "empty DNS host" }
            HostPort(host, parsePort(value.substring(separator + 1)))
        } else {
            HostPort(value, null)
        }
    }

    private fun parsePort(raw: String): Int {
        val port = raw.toIntOrNull()
        require(port != null && port in 1..65535) { "invalid port: $raw" }
        return port
    }

    private fun ipv6Strategy(mode: Ipv6Mode): String = when (mode) {
        Ipv6Mode.DISABLE -> "ipv4_only"
        Ipv6Mode.ENABLE -> "prefer_ipv4"
        Ipv6Mode.PREFER -> "prefer_ipv6"
        Ipv6Mode.ONLY -> "ipv6_only"
    }

    private fun alpnArray(value: String): JsonArray = stringArray(
        value.split(',').map(String::trim).filter(String::isNotEmpty),
    )

    private fun stringArray(values: Iterable<String>): JsonArray = buildJsonArray {
        values.forEach { add(JsonPrimitive(it)) }
    }

    private fun withFields(base: JsonObject, append: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
        base.forEach { (key, value) -> put(key, value) }
        append()
    }

    private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.int(key: String): Int? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull()
    }

    private fun JsonObject.boolean(key: String): Boolean {
        val primitive = this[key] as? JsonPrimitive ?: return false
        return primitive.booleanOrNull ?: primitive.contentOrNull.equals("1")
    }

    private fun JsonElement?.primitiveContent(): String? = (this as? JsonPrimitive)?.contentOrNull

    private data class HostPort(val host: String, val port: Int?)
}
