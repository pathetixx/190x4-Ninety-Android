package pw.x4.ninety.data

import org.json.JSONObject

/**
 * Узел (нода) — один прокси-сервер. Поля — надмножество всех протоколов
 * (порт из singbox.js). Пустые/0 = не задано. proto: vless|vmess|trojan|
 * shadowsocks|hysteria2|tuic. xhttp-транспорт помечается type="xhttp" и в M2
 * считается неподдерживаемым (нужен xray, M3).
 */
data class Node(
    val proto: String,
    val name: String,
    val host: String,
    val port: Int,
    val uuid: String = "",
    val password: String = "",
    val method: String = "",       // shadowsocks-метод
    val cipher: String = "auto",   // vmess-шифр (scy)
    val alterId: Int = 0,
    val security: String = "none", // TLS-режим: tls|reality|none
    val type: String = "tcp",      // transport: tcp|ws|grpc|http|xhttp
    val flow: String = "",
    val sni: String = "",
    val fp: String = "chrome",
    val pbk: String = "",
    val sid: String = "",
    val alpn: String = "",
    val path: String = "",
    val hostHeader: String = "",
    val serviceName: String = "",
    val mode: String = "",
    val upMbps: Int = 0,
    val downMbps: Int = 0,
    val obfs: String = "",
    val obfsPassword: String = "",
    val congestion: String = "",
    val udpRelay: String = "",
    val insecure: Boolean = false,
    val zeroRtt: Boolean = false,
    val raw: String = "",
    val fromSub: Boolean = false,  // пришла из подписки → заменяется при refresh
    val subId: String = "",        // id профиля-владельца (Profile.id); "" = legacy
) {
    /** Стабильный id для выбора/персиста. */
    val id: String get() = "$proto|$host|$port|$uuid$password".hashCode().toString()

    /** Поддерживается движком M2 (без xray). xhttp — нет. */
    val supported: Boolean get() = type != "xhttp"

    fun toJson(): JSONObject = JSONObject().apply {
        put("proto", proto); put("name", name); put("host", host); put("port", port)
        put("uuid", uuid); put("password", password); put("method", method); put("cipher", cipher); put("alterId", alterId)
        put("security", security); put("type", type); put("flow", flow); put("sni", sni)
        put("fp", fp); put("pbk", pbk); put("sid", sid); put("alpn", alpn); put("path", path)
        put("hostHeader", hostHeader); put("serviceName", serviceName); put("mode", mode)
        put("upMbps", upMbps); put("downMbps", downMbps); put("obfs", obfs); put("obfsPassword", obfsPassword)
        put("congestion", congestion); put("udpRelay", udpRelay); put("insecure", insecure)
        put("zeroRtt", zeroRtt); put("raw", raw); put("fromSub", fromSub); put("subId", subId)
    }

    companion object {
        fun fromJson(o: JSONObject) = Node(
            proto = o.optString("proto", "vless"),
            name = o.optString("name"),
            host = o.optString("host"),
            port = o.optInt("port"),
            uuid = o.optString("uuid"),
            password = o.optString("password"),
            method = o.optString("method"),
            cipher = o.optString("cipher", "auto"),
            alterId = o.optInt("alterId"),
            security = o.optString("security", "none"),
            type = o.optString("type", "tcp"),
            flow = o.optString("flow"),
            sni = o.optString("sni"),
            fp = o.optString("fp", "chrome"),
            pbk = o.optString("pbk"),
            sid = o.optString("sid"),
            alpn = o.optString("alpn"),
            path = o.optString("path"),
            hostHeader = o.optString("hostHeader"),
            serviceName = o.optString("serviceName"),
            mode = o.optString("mode"),
            upMbps = o.optInt("upMbps"),
            downMbps = o.optInt("downMbps"),
            obfs = o.optString("obfs"),
            obfsPassword = o.optString("obfsPassword"),
            congestion = o.optString("congestion"),
            udpRelay = o.optString("udpRelay"),
            insecure = o.optBoolean("insecure"),
            zeroRtt = o.optBoolean("zeroRtt"),
            raw = o.optString("raw"),
            fromSub = o.optBoolean("fromSub"),
            subId = o.optString("subId"),
        )
    }
}
