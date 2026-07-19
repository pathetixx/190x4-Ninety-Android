package pw.x4.ninety.core.parser

/** Протоколы share-ссылок, общие для desktop и Android. */
enum class ProxyProtocol(val wireName: String) {
    VLESS("vless"),
    VMESS("vmess"),
    TROJAN("trojan"),
    SHADOWSOCKS("shadowsocks"),
    HYSTERIA2("hysteria2"),
    TUIC("tuic"),
}

/**
 * Нормализованный результат разбора share-ссылки.
 *
 * Модель не зависит от Android, UI, хранения и формата sing-box JSON. Поля повторяют
 * общий контракт desktop `protocol-parsers.js`; платформенный слой позже решает, какие
 * возможности реально поддерживаются текущим runtime.
 */
data class ParsedProxy(
    val protocol: ProxyProtocol,
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
    val fingerprint: String = "chrome",
    val publicKey: String = "",
    val shortId: String = "",
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
    val congestionControl: String = "",
    val udpRelayMode: String = "",
    val insecure: Boolean = false,
    val zeroRttHandshake: Boolean = false,
    val plugin: String = "",
    val pluginOptions: String = "",
    val raw: String,
)
