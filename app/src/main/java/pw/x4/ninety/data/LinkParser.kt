package pw.x4.ninety.data

import android.net.Uri
import android.util.Base64
import org.json.JSONObject

/**
 * Парсеры share-ссылок → [Node]. Порт singbox.js (parseVless/Vmess/Trojan/SS/
 * Hysteria2/Tuic). Использует android.net.Uri для userinfo@host:port?query#name.
 */
object LinkParser {

    fun parseLink(raw: String): Node? {
        val s = raw.trim()
        return try {
            when {
                s.startsWith("vless://") -> parseVless(s)
                s.startsWith("vmess://") -> parseVmess(s)
                s.startsWith("trojan://") -> parseTrojan(s)
                s.startsWith("ss://") -> parseShadowsocks(s)
                s.startsWith("hysteria2://") || s.startsWith("hy2://") -> parseHysteria2(s)
                s.startsWith("tuic://") -> parseTuic(s)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Содержимое подписки: либо base64 со списком ссылок, либо plain список строк.
     * Возвращает все распарсенные ноды (битые пропускает).
     */
    fun parseSubscription(content: String): List<Node> {
        val text = content.trim()
        // base64-подписка vs plain-список: берём декод только если в нём есть схемы.
        val decoded = tryBase64(text)
        val body = if (decoded != null && decoded.contains("://")) decoded else text
        return body.split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseLink(it) }
    }

    private fun parseVless(s: String): Node {
        val u = Uri.parse(s)
        val q = QueryView(u)
        val type = q["type"] ?: "tcp"
        return Node(
            proto = "vless",
            name = u.fragment ?: "VLESS",
            host = u.host ?: error("no host"),
            port = u.port.takeIf { it > 0 } ?: error("no port"),
            uuid = u.userInfo ?: "",
            security = q["security"] ?: "none",
            type = type,
            flow = q["flow"] ?: "",
            sni = q["sni"] ?: u.host ?: "",
            fp = q["fp"] ?: "chrome",
            pbk = q["pbk"] ?: "",
            sid = q["sid"] ?: "",
            alpn = q["alpn"] ?: "",
            path = q["path"] ?: "",
            hostHeader = q["host"] ?: "",
            serviceName = q["serviceName"] ?: "",
            mode = q["mode"] ?: "",
            raw = s,
        )
    }

    private fun parseTrojan(s: String): Node {
        val u = Uri.parse(s)
        val q = QueryView(u)
        return Node(
            proto = "trojan",
            name = u.fragment ?: "Trojan",
            host = u.host ?: error("no host"),
            port = u.port.takeIf { it > 0 } ?: error("no port"),
            password = u.userInfo ?: "",
            security = q["security"] ?: "tls",
            type = q["type"] ?: "tcp",
            sni = q["sni"] ?: u.host ?: "",
            fp = q["fp"] ?: "chrome",
            alpn = q["alpn"] ?: "",
            path = q["path"] ?: "",
            hostHeader = q["host"] ?: "",
            serviceName = q["serviceName"] ?: "",
            raw = s,
        )
    }

    private fun parseVmess(s: String): Node {
        val json = tryBase64(s.removePrefix("vmess://")) ?: error("bad vmess base64")
        val o = JSONObject(json)
        val tls = o.optString("tls")
        return Node(
            proto = "vmess",
            name = o.optString("ps", "VMess"),
            host = o.optString("add"),
            port = o.optString("port").toIntOrNull() ?: o.optInt("port"),
            uuid = o.optString("id"),
            cipher = o.optString("scy").ifBlank { "auto" },
            alterId = o.optString("aid").toIntOrNull() ?: o.optInt("aid"),
            security = if (tls == "tls") "tls" else "none",
            type = o.optString("net", "tcp"),
            sni = o.optString("sni").ifBlank { o.optString("add") },
            alpn = o.optString("alpn"),
            fp = o.optString("fp").ifBlank { "chrome" },
            path = o.optString("path"),
            hostHeader = o.optString("host"),
            serviceName = o.optString("path"), // grpc serviceName иногда в path
            raw = s,
        )
    }

    private fun parseShadowsocks(s: String): Node {
        val body = s.removePrefix("ss://")
        val hashIdx = body.indexOf('#')
        val name = if (hashIdx >= 0) Uri.decode(body.substring(hashIdx + 1)) else "Shadowsocks"
        val main = if (hashIdx >= 0) body.substring(0, hashIdx) else body
        val qIdx = main.indexOf('?')
        val core = if (qIdx >= 0) main.substring(0, qIdx) else main

        val method: String; val password: String; val host: String; val port: Int
        val atIdx = core.lastIndexOf('@')
        if (atIdx >= 0) {
            // SIP002: ss://base64(method:pass)@host:port
            val creds = tryBase64(core.substring(0, atIdx)) ?: core.substring(0, atIdx)
            val ci = creds.indexOf(':')
            method = creds.substring(0, ci)
            password = creds.substring(ci + 1)
            val hp = core.substring(atIdx + 1)
            val pi = hp.lastIndexOf(':')
            host = hp.substring(0, pi)
            port = hp.substring(pi + 1).toInt()
        } else {
            // legacy: ss://base64(method:pass@host:port)
            val dec = tryBase64(core) ?: error("bad ss base64")
            val a = dec.lastIndexOf('@')
            val creds = dec.substring(0, a)
            val ci = creds.indexOf(':')
            method = creds.substring(0, ci)
            password = creds.substring(ci + 1)
            val hp = dec.substring(a + 1)
            val pi = hp.lastIndexOf(':')
            host = hp.substring(0, pi)
            port = hp.substring(pi + 1).toInt()
        }
        return Node(proto = "shadowsocks", name = name, host = host, port = port,
            method = method, password = password, raw = s)
    }

    private fun parseHysteria2(s: String): Node {
        val u = Uri.parse(s)
        val q = QueryView(u)
        return Node(
            proto = "hysteria2",
            name = u.fragment ?: "Hysteria2",
            host = u.host ?: error("no host"),
            port = u.port.takeIf { it > 0 } ?: 443,
            password = u.userInfo ?: "",
            sni = q["sni"] ?: u.host ?: "",
            insecure = q["insecure"] == "1" || q["insecure"] == "true",
            obfs = q["obfs"] ?: "",
            obfsPassword = q["obfs-password"] ?: "",
            upMbps = q["up"]?.toIntOrNull() ?: 0,
            downMbps = q["down"]?.toIntOrNull() ?: 0,
            alpn = q["alpn"] ?: "h3",
            raw = s,
        )
    }

    private fun parseTuic(s: String): Node {
        val u = Uri.parse(s)
        val q = QueryView(u)
        val ui = u.userInfo ?: ""
        val ci = ui.indexOf(':')
        val uuid = if (ci >= 0) ui.substring(0, ci) else ui
        val password = if (ci >= 0) ui.substring(ci + 1) else ""
        return Node(
            proto = "tuic",
            name = u.fragment ?: "TUIC",
            host = u.host ?: error("no host"),
            port = u.port.takeIf { it > 0 } ?: 443,
            uuid = uuid,
            password = password,
            congestion = q["congestion_control"] ?: "bbr",
            udpRelay = q["udp_relay_mode"] ?: "native",
            sni = q["sni"] ?: u.host ?: "",
            alpn = q["alpn"] ?: "h3",
            insecure = q["allow_insecure"] == "1" || q["allow_insecure"] == "true",
            zeroRtt = q["zero_rtt_handshake"] == "1" || q["zero_rtt_handshake"] == "true",
            raw = s,
        )
    }

    /** Декод base64: пробуем std (+/), затем url-алфавит (-_). null если не base64. */
    private fun tryBase64(s: String): String? {
        val cleaned = s.replace("\\s".toRegex(), "")
        if (cleaned.isEmpty()) return null
        for (flags in intArrayOf(Base64.NO_WRAP, Base64.NO_WRAP or Base64.URL_SAFE)) {
            try {
                return String(Base64.decode(cleaned, flags), Charsets.UTF_8)
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** Обёртка для безопасного чтения query-параметров (null если нет). */
    private class QueryView(private val uri: Uri) {
        operator fun get(key: String): String? =
            try { uri.getQueryParameter(key)?.takeIf { it.isNotEmpty() } } catch (_: Exception) { null }
    }
}
