package pw.x4.ninety.data

import org.json.JSONObject

/**
 * Узел (нода) — один прокси-сервер. Поля являются надмножеством поддерживаемых
 * протоколов libbox. Пустые строки и нули означают, что параметр не задан.
 */
data class Node(
    val proto: String,
    val name: String,
    val host: String,
    val port: Int,
    val uuid: String = "",
    val password: String = "",
    val method: String = "",
    val cipher: String = "auto",
    val alterId: Int = 0,
    val security: String = "none",
    val type: String = "tcp",
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
    val extra: String = "",
    val upMbps: Int = 0,
    val downMbps: Int = 0,
    val obfs: String = "",
    val obfsPassword: String = "",
    val pinSHA256: String = "",
    val congestion: String = "",
    val udpRelay: String = "",
    val insecure: Boolean = false,
    val zeroRtt: Boolean = false,
    val disableSni: Boolean = false,
    val plugin: String = "",
    val pluginOpts: String = "",
    val raw: String = "",
    val fromSub: Boolean = false,
    val subId: String = "",
) {
    /** Fingerprint of every connection-affecting field, independent of display name/profile. */
    val fingerprint: String
        get() = StableId.sha256(
            buildString {
                listOf(
                    proto,
                    host.lowercase(),
                    port.toString(),
                    uuid,
                    password,
                    method,
                    cipher,
                    alterId.toString(),
                    security,
                    type,
                    flow,
                    sni,
                    fp,
                    pbk,
                    sid,
                    alpn,
                    path,
                    hostHeader,
                    serviceName,
                    mode,
                    extra,
                    upMbps.toString(),
                    downMbps.toString(),
                    obfs,
                    obfsPassword,
                    pinSHA256,
                    congestion,
                    udpRelay,
                    insecure.toString(),
                    zeroRtt.toString(),
                    disableSni.toString(),
                    plugin,
                    pluginOpts,
                ).forEach { value ->
                    append(value.length).append(':').append(value).append('|')
                }
            },
        )

    /** Profile-scoped stable id; the same endpoint may safely exist in multiple profiles. */
    val id: String get() = StableId.node(subId, fingerprint)

    /** Все текущие протоколы и xHTTP поддержаны нативным libbox fork. */
    val supported: Boolean get() = true

    val isXhttp: Boolean get() = type == "xhttp"

    fun toJson(): JSONObject = JSONObject().apply {
        put("proto", proto); put("name", name); put("host", host); put("port", port)
        put("uuid", uuid); put("password", password); put("method", method); put("cipher", cipher); put("alterId", alterId)
        put("security", security); put("type", type); put("flow", flow); put("sni", sni)
        put("fp", fp); put("pbk", pbk); put("sid", sid); put("alpn", alpn); put("path", path)
        put("hostHeader", hostHeader); put("serviceName", serviceName); put("mode", mode)
        put("extra", extra)
        put("upMbps", upMbps); put("downMbps", downMbps); put("obfs", obfs); put("obfsPassword", obfsPassword)
        put("pinSHA256", pinSHA256); put("congestion", congestion); put("udpRelay", udpRelay); put("insecure", insecure)
        put("zeroRtt", zeroRtt); put("disableSni", disableSni); put("plugin", plugin); put("pluginOpts", pluginOpts)
        put("raw", raw); put("fromSub", fromSub); put("subId", subId)
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
            extra = o.optString("extra"),
            upMbps = o.optInt("upMbps"),
            downMbps = o.optInt("downMbps"),
            obfs = o.optString("obfs"),
            obfsPassword = o.optString("obfsPassword"),
            pinSHA256 = o.optString("pinSHA256"),
            congestion = o.optString("congestion"),
            udpRelay = o.optString("udpRelay"),
            insecure = o.optBoolean("insecure"),
            zeroRtt = o.optBoolean("zeroRtt"),
            disableSni = o.optBoolean("disableSni"),
            plugin = o.optString("plugin"),
            pluginOpts = o.optString("pluginOpts"),
            raw = o.optString("raw"),
            fromSub = o.optBoolean("fromSub"),
            subId = o.optString("subId"),
        )
    }
}
