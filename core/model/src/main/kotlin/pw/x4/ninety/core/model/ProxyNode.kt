package pw.x4.ninety.core.model

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
 * Нормализованная кроссплатформенная нода.
 *
 * Модель не зависит от Android, UI, хранения и формата sing-box JSON. Поля повторяют
 * общий контракт desktop `protocol-parsers.js`; platform capabilities и config builder
 * решают, какие возможности реально поддерживаются конкретным runtime.
 */
data class ProxyNode(
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
    val certificatePublicKeySha256: String = "",
    val congestionControl: String = "",
    val udpRelayMode: String = "",
    val insecure: Boolean = false,
    val zeroRttHandshake: Boolean = false,
    val disableSni: Boolean = false,
    val plugin: String = "",
    val pluginOptions: String = "",
    val raw: String,
)
