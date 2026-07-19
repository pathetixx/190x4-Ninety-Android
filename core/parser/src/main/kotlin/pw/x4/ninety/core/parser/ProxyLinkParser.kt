package pw.x4.ninety.core.parser

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure Kotlin/JVM parser for Ninety share links and subscription payloads.
 *
 * The implementation follows the desktop `src/lib/protocol-parsers.js` contract but
 * keeps useful Android compatibility aliases (`hy2`, boolean `1/true`, default QUIC
 * port 443). Malformed links are isolated: one bad subscription row never poisons the
 * remaining valid nodes.
 */
object ProxyLinkParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val knownSchemes = listOf(
        "vless://",
        "vmess://",
        "trojan://",
        "ss://",
        "hysteria2://",
        "hy2://",
        "tuic://",
    )

    fun parseLink(raw: String): ParsedProxy? {
        val value = raw.trim().removePrefix("\uFEFF")
        if (value.isEmpty()) return null
        return runCatching {
            when {
                value.startsWith("vless://") -> parseVless(value)
                value.startsWith("vmess://") -> parseVmess(value)
                value.startsWith("trojan://") -> parseTrojan(value)
                value.startsWith("ss://") -> parseShadowsocks(value)
                value.startsWith("hysteria2://") || value.startsWith("hy2://") -> parseHysteria2(value)
                value.startsWith("tuic://") -> parseTuic(value)
                else -> null
            }
        }.getOrNull()
    }

    /** Accepts a plain line list or a standard/url-safe base64 encoded line list. */
    fun parseSubscription(content: String): List<ParsedProxy> {
        val source = content.trim().removePrefix("\uFEFF")
        if (source.isEmpty()) return emptyList()

        val decoded = decodeBase64(source)
        val body = decoded?.takeIf { text -> knownSchemes.any { scheme -> text.contains(scheme) } } ?: source
        return body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull(::parseLink)
            .toList()
    }

    private fun parseVless(url: String): ParsedProxy {
        val payload = url.removePrefix("vless://")
        val (main, name) = splitTrailingName(payload, "VLESS")
        val (head, query) = splitQuery(main)
        val at = head.lastIndexOf('@')
        require(at > 0) { "vless: missing credentials or host" }
        val endpoint = splitHostPort(head.substring(at + 1))
        val uuid = decodeComponent(head.substring(0, at))
        require(uuid.isNotBlank()) { "vless: missing uuid" }

        return ParsedProxy(
            protocol = ProxyProtocol.VLESS,
            name = name,
            host = endpoint.host,
            port = endpoint.port,
            uuid = uuid,
            security = query["security"] ?: "none",
            type = query["type"] ?: "tcp",
            flow = query["flow"].orEmpty(),
            sni = query["sni"].orEmpty().ifBlank { endpoint.host },
            fingerprint = query["fp"] ?: "chrome",
            publicKey = query["pbk"].orEmpty(),
            shortId = query["sid"].orEmpty(),
            alpn = query["alpn"].orEmpty(),
            path = query["path"].orEmpty(),
            hostHeader = query["host"].orEmpty(),
            serviceName = query["serviceName"].orEmpty(),
            mode = query["mode"].orEmpty(),
            extra = query["extra"].orEmpty(),
            raw = url,
        )
    }

    private fun parseVmess(url: String): ParsedProxy {
        val payload = url.removePrefix("vmess://")
        val hash = payload.indexOf('#')
        val encoded = if (hash >= 0) payload.substring(0, hash) else payload
        val hashName = if (hash >= 0) decodeComponent(payload.substring(hash + 1)) else ""
        val decoded = requireNotNull(decodeBase64(encoded)) { "vmess: invalid base64" }
        val data = json.parseToJsonElement(decoded).jsonObject

        val host = data.string("add")
        require(host.isNotBlank()) { "vmess: missing host" }
        val port = parsePort(data.string("port"), "vmess")
        val uuid = data.string("id")
        require(uuid.isNotBlank()) { "vmess: missing uuid" }
        val tlsMode = data.string("tls").lowercase()
        val name = hashName.ifBlank {
            data.string("ps").ifBlank { data.string("remarks").ifBlank { "VMESS" } }
        }

        return ParsedProxy(
            protocol = ProxyProtocol.VMESS,
            name = name,
            host = host,
            port = port,
            uuid = uuid,
            cipher = data.string("scy").ifBlank { data.string("security").ifBlank { "auto" } },
            alterId = data.string("aid").ifBlank { data.string("alterId") }.toIntOrNull() ?: 0,
            security = tlsMode.takeIf { it == "tls" || it == "reality" } ?: "none",
            type = data.string("net").ifBlank { "tcp" },
            sni = data.string("sni").ifBlank { data.string("host").ifBlank { host } },
            fingerprint = data.string("fp").ifBlank { "chrome" },
            alpn = data.string("alpn"),
            path = data.string("path"),
            hostHeader = data.string("host"),
            serviceName = data.string("path"),
            mode = data.string("type"),
            raw = url,
        )
    }

    private fun parseTrojan(url: String): ParsedProxy {
        val payload = url.removePrefix("trojan://")
        val (main, name) = splitTrailingName(payload, "TROJAN")
        val (head, query) = splitQuery(main)
        val at = head.lastIndexOf('@')
        require(at > 0) { "trojan: missing password or host" }
        val endpoint = splitHostPort(head.substring(at + 1))

        return ParsedProxy(
            protocol = ProxyProtocol.TROJAN,
            name = name,
            host = endpoint.host,
            port = endpoint.port,
            password = decodeComponent(head.substring(0, at)),
            security = query["security"] ?: "tls",
            type = query["type"] ?: "tcp",
            sni = query["sni"].orEmpty().ifBlank { endpoint.host },
            fingerprint = query["fp"] ?: "chrome",
            alpn = query["alpn"].orEmpty(),
            path = query["path"].orEmpty(),
            hostHeader = query["host"].orEmpty(),
            serviceName = query["serviceName"].orEmpty(),
            mode = query["mode"].orEmpty(),
            extra = query["extra"].orEmpty(),
            raw = url,
        )
    }

    private fun parseShadowsocks(url: String): ParsedProxy {
        val payload = url.removePrefix("ss://")
        val (main, name) = splitTrailingName(payload, "SS")
        val (head, query) = splitQuery(main)
        val at = head.lastIndexOf('@')

        val method: String
        val password: String
        val endpoint: HostPort
        if (at < 0) {
            val decoded = requireNotNull(decodeBase64(head)) { "ss: invalid legacy base64" }
            val decodedAt = decoded.lastIndexOf('@')
            require(decodedAt > 0) { "ss: missing host" }
            val credentials = decoded.substring(0, decodedAt)
            val separator = credentials.indexOf(':')
            require(separator > 0) { "ss: invalid credentials" }
            method = credentials.substring(0, separator)
            password = credentials.substring(separator + 1)
            endpoint = splitHostPort(decoded.substring(decodedAt + 1))
        } else {
            val credentials = head.substring(0, at)
            val decodedCredentials = if (credentials.contains(':')) credentials else {
                requireNotNull(decodeBase64(credentials)) { "ss: invalid credentials base64" }
            }
            val separator = decodedCredentials.indexOf(':')
            require(separator > 0) { "ss: invalid credentials" }
            method = decodeComponent(decodedCredentials.substring(0, separator))
            password = decodeComponent(decodedCredentials.substring(separator + 1))
            endpoint = splitHostPort(head.substring(at + 1))
        }

        val pluginValue = query["plugin"].orEmpty()
        val pluginSeparator = pluginValue.indexOf(';')
        val plugin = if (pluginSeparator >= 0) pluginValue.substring(0, pluginSeparator) else pluginValue
        val pluginOptions = if (pluginSeparator >= 0) pluginValue.substring(pluginSeparator + 1) else ""

        return ParsedProxy(
            protocol = ProxyProtocol.SHADOWSOCKS,
            name = name,
            host = endpoint.host,
            port = endpoint.port,
            method = method,
            password = password,
            plugin = plugin,
            pluginOptions = pluginOptions,
            raw = url,
        )
    }

    private fun parseHysteria2(url: String): ParsedProxy {
        val scheme = if (url.startsWith("hysteria2://")) "hysteria2://" else "hy2://"
        val payload = url.removePrefix(scheme)
        val (main, name) = splitTrailingName(payload, "HYSTERIA2")
        val (head, query) = splitQuery(main)
        val at = head.lastIndexOf('@')
        require(at > 0) { "hysteria2: missing password or host" }
        val endpoint = splitHostPort(head.substring(at + 1), defaultPort = 443)

        return ParsedProxy(
            protocol = ProxyProtocol.HYSTERIA2,
            name = name,
            host = endpoint.host,
            port = endpoint.port,
            password = decodeComponent(head.substring(0, at)),
            sni = query["sni"].orEmpty().ifBlank { endpoint.host },
            alpn = query["alpn"] ?: "h3",
            insecure = query["insecure"].toBooleanFlag(),
            obfs = query["obfs"].orEmpty(),
            obfsPassword = query["obfs-password"] ?: query["obfsPassword"].orEmpty(),
            upMbps = query["up"].toPositiveIntOrZero(),
            downMbps = query["down"].toPositiveIntOrZero(),
            raw = url,
        )
    }

    private fun parseTuic(url: String): ParsedProxy {
        val payload = url.removePrefix("tuic://")
        val (main, name) = splitTrailingName(payload, "TUIC")
        val (head, query) = splitQuery(main)
        val at = head.lastIndexOf('@')
        require(at > 0) { "tuic: missing auth or host" }
        val auth = head.substring(0, at)
        val separator = auth.indexOf(':')
        val uuid = decodeComponent(if (separator >= 0) auth.substring(0, separator) else auth)
        val password = decodeComponent(if (separator >= 0) auth.substring(separator + 1) else "")
        require(uuid.isNotBlank()) { "tuic: missing uuid" }
        val endpoint = splitHostPort(head.substring(at + 1), defaultPort = 443)

        return ParsedProxy(
            protocol = ProxyProtocol.TUIC,
            name = name,
            host = endpoint.host,
            port = endpoint.port,
            uuid = uuid,
            password = password,
            sni = query["sni"].orEmpty().ifBlank { endpoint.host },
            alpn = query["alpn"] ?: "h3",
            congestionControl = query["congestion_control"] ?: query["congestionControl"] ?: "bbr",
            udpRelayMode = query["udp_relay_mode"] ?: query["udpRelayMode"] ?: "native",
            insecure = query["allow_insecure"].toBooleanFlag(),
            zeroRttHandshake = query["zero_rtt_handshake"].toBooleanFlag(),
            raw = url,
        )
    }

    private fun splitTrailingName(value: String, defaultName: String): Pair<String, String> {
        val hash = value.indexOf('#')
        if (hash < 0) return value to defaultName
        val name = decodeComponent(value.substring(hash + 1)).ifBlank { defaultName }
        return value.substring(0, hash) to name
    }

    private fun splitQuery(value: String): Pair<String, Map<String, String>> {
        val question = value.indexOf('?')
        if (question < 0) return value to emptyMap()
        return value.substring(0, question) to parseQuery(value.substring(question + 1))
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        raw.split('&').forEach { part ->
            if (part.isEmpty()) return@forEach
            val separator = part.indexOf('=')
            val key = decodeQuery(if (separator >= 0) part.substring(0, separator) else part)
            val value = decodeQuery(if (separator >= 0) part.substring(separator + 1) else "")
            result.putIfAbsent(key, value)
        }
        return result
    }

    private fun splitHostPort(value: String, defaultPort: Int? = null): HostPort {
        val raw = value.trim()
        require(raw.isNotEmpty()) { "missing host" }
        if (raw.startsWith("[")) {
            val closing = raw.indexOf(']')
            require(closing > 1) { "invalid IPv6 host" }
            val host = raw.substring(1, closing)
            val port = when {
                closing + 1 == raw.length -> requireNotNull(defaultPort) { "missing port" }
                raw.getOrNull(closing + 1) == ':' -> parsePort(raw.substring(closing + 2), "endpoint")
                else -> error("invalid IPv6 endpoint")
            }
            return HostPort(host, port)
        }

        val colon = raw.lastIndexOf(':')
        if (colon <= 0) return HostPort(raw, requireNotNull(defaultPort) { "missing port" })
        val host = raw.substring(0, colon)
        require(host.isNotBlank()) { "missing host" }
        return HostPort(host, parsePort(raw.substring(colon + 1), "endpoint"))
    }

    private fun parsePort(raw: String, label: String): Int {
        val port = raw.toIntOrNull() ?: error("$label: invalid port")
        require(port in 1..65535) { "$label: port out of range" }
        return port
    }

    private fun decodeBase64(value: String): String? {
        val cleaned = value.filterNot { it.isWhitespace() }
        if (cleaned.isEmpty()) return null
        val padded = cleaned + "=".repeat((4 - cleaned.length % 4) % 4)
        for (decoder in listOf(Base64.getDecoder(), Base64.getUrlDecoder())) {
            try {
                return decoder.decode(padded).toString(StandardCharsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                // Try the other alphabet.
            }
        }
        return null
    }

    private fun decodeComponent(value: String): String =
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8)

    private fun decodeQuery(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)

    private fun String?.toBooleanFlag(): Boolean = when (this?.lowercase()) {
        "1", "true", "yes", "on" -> true
        else -> false
    }

    private fun String?.toPositiveIntOrZero(): Int = this?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private data class HostPort(val host: String, val port: Int)
}
