package pw.x4.ninety.vpn

import org.json.JSONArray
import org.json.JSONObject
import pw.x4.ninety.data.Node
import pw.x4.ninety.data.Options

/**
 * Генератор sing-box JSON под Android (порт singbox.js). Всегда tun-inbound
 * (фактический tun отдаёт libbox.OpenTun через VpnService). Узлы всегда заворачиваются
 * в selector "proxy" (default = выбранная нода | "auto") + urltest "auto": urltest даёт
 * авто-выбор по задержке (.now = быстрейший) и пинги нод для UI (group-стрим CommandClient).
 * xhttp-узлы вызывающий код отсеивает заранее (нужен xray, M3).
 *
 * ⚙️ Параметризован через [Options]: при дефолтных опциях выдаёт КОНФИГ, идентичный
 * прежнему захардкоженному (проверен на железе). Каждая опция влияет на JSON только при
 * отклонении от дефолта — рабочий туннель/переключение не ломаются у тех, кто не лез в
 * настройки. Ключи tls-tricks/mux/dns/rule_set взяты 1-в-1 из desktop singbox.js
 * (строгий парсер форка 1.13: неизвестное поле роняет ВЕСЬ конфиг).
 */
object ConfigBuilder {

    private const val GEO_BASE = "https://raw.githubusercontent.com/hiddify/hiddify-geo/rule-set"
    private val BLOCK_AD_SETS = listOf(
        "geosite-ads" to "$GEO_BASE/block/geosite-category-ads-all.srs",
        "geosite-malware" to "$GEO_BASE/block/geosite-malware.srs",
        "geosite-phishing" to "$GEO_BASE/block/geosite-phishing.srs",
        "geosite-cryptominers" to "$GEO_BASE/block/geosite-cryptominers.srs",
        "geoip-malware" to "$GEO_BASE/block/geoip-malware.srs",
        "geoip-phishing" to "$GEO_BASE/block/geoip-phishing.srs",
    )

    fun build(
        nodes: List<Node>,
        selectedId: String?,
        logPath: String? = null,
        opts: Options.Data = Options.data,
    ): String {
        require(nodes.isNotEmpty()) { "пустой список нод" }

        // Дедуп по идентичности — иначе две одинаковые ноды дают один и тот же
        // outbound-tag, и ядро падает на дубликате.
        val uniq = nodes.distinctBy { it.id }

        val nodeOutbounds = JSONArray()
        val nodeTags = ArrayList<String>()
        var selectedTag: String? = null
        uniq.forEach { n ->
            val tag = tagOf(n)
            nodeTags.add(tag)
            if (n.id == selectedId) selectedTag = tag
            // xhttp теперь нативный outbound форка (см. transport()), как обычный vless
            nodeOutbounds.put(outbound(n, tag, opts))
        }
        val validSelected = selectedTag?.takeIf { nodeTags.contains(it) }

        // Всегда selector "proxy" (default = выбранная нода | "auto") + urltest "auto".
        // urltest держим ВСЕГДА: он и health-checker авто (его .now = быстрейший узел,
        // как на desktop), и единственный источник пингов нод для UI (CommandClient
        // group-стрим: URLTestDelay по тегам).
        val outbounds = JSONArray()
        val tagsArr = JSONArray().apply { nodeTags.forEach { put(it) } }
        outbounds.put(JSONObject().apply {
            put("type", "selector"); put("tag", "proxy")
            put("outbounds", JSONArray().apply { put("auto"); nodeTags.forEach { put(it) } })
            put("default", validSelected ?: "auto")
            put("interrupt_exist_connections", true)
        })
        outbounds.put(JSONObject().apply {
            put("type", "urltest"); put("tag", "auto")
            put("outbounds", tagsArr)
            put("url", opts.testUrl); put("interval", "${opts.testIntervalSec}s"); put("tolerance", 50)
            put("interrupt_exist_connections", false)
        })
        for (i in 0 until nodeOutbounds.length()) outbounds.put(nodeOutbounds.get(i))
        outbounds.put(JSONObject().apply { put("type", "direct"); put("tag", "direct") })

        val config = JSONObject().apply {
            put("log", JSONObject().apply {
                if (opts.logDisabled) {
                    put("disabled", true)
                } else {
                    // output=файл → ядро пишет лог само (platform-callback логи не отдаёт).
                    put("level", opts.logLevel); put("timestamp", true)
                    if (logPath != null) put("output", logPath)
                }
            })
            put("dns", dns(opts))
            put("inbounds", JSONArray().put(tunInbound(opts)))
            put("outbounds", outbounds)
            put("route", route(opts))
            put("experimental", experimental())
        }
        return config.toString()
    }

    private fun tunInbound(opts: Options.Data) = JSONObject().apply {
        put("type", "tun"); put("tag", "tun-in")
        put("address", JSONArray().apply {
            put("172.19.0.1/30")
            // v6-адрес только если IPv6 включён — иначе анонс ::/0 без v6-outbound
            // вешал dial на ~30с (баг be2f087). При disable остаёмся чисто v4.
            if (opts.ipv6Mode != "disable") put("fdfe:dcba:9876::1/126")
        })
        put("mtu", opts.mtu)
        put("auto_route", true)
        put("strict_route", opts.strictRoute)
        put("stack", opts.tunStack)
    }

    private fun ipv6Strategy(mode: String) = when (mode) {
        "enable" -> "prefer_ipv4"
        "prefer" -> "prefer_ipv6"
        "only" -> "ipv6_only"
        else -> "ipv4_only"
    }

    private fun dns(opts: Options.Data) = JSONObject().apply {
        val remote = parseDnsAddress(opts.dnsRemote).apply {
            put("tag", "dns-remote"); put("detour", "proxy"); put("domain_resolver", "dns-direct")
        }
        // detour:direct ОБЯЗАТЕЛЕН — иначе «прямой» резолвер сам идёт через route.final=proxy
        // (deadlock; фикс f430889 на железе).
        val direct = parseDnsAddress(opts.dnsDirect).apply {
            put("tag", "dns-direct"); put("detour", "direct")
        }
        val servers = JSONArray().put(remote).put(direct)
        val rules = JSONArray()

        if (opts.region != "other" && opts.region.isNotBlank()) {
            rules.put(JSONObject().apply {
                put("domain_suffix", JSONArray().put(".${opts.region}"))
                put("server", "dns-direct"); put("rewrite_ttl", 86400)
            })
            rules.put(JSONObject().apply {
                put("rule_set", JSONArray().put("geosite-${opts.region}"))
                put("server", "dns-direct"); put("rewrite_ttl", 86400)
            })
        }
        if (opts.fakeDns) {
            servers.put(JSONObject().apply {
                put("tag", "dns-fake"); put("type", "fakeip")
                put("inet4_range", "198.18.0.0/15"); put("inet6_range", "fc00::/18")
            })
            rules.put(JSONObject().apply {
                put("query_type", JSONArray().put("A").put("AAAA")); put("server", "dns-fake")
            })
        }

        put("servers", servers)
        if (rules.length() > 0) put("rules", rules)
        // ipv4_only: не отдаём AAAA → апп не пробует IPv6 первым (у нод нет v6-outbound).
        put("strategy", ipv6Strategy(opts.ipv6Mode))
        if (opts.independentCache) put("independent_cache", true)
        put("final", "dns-remote")
    }

    private fun route(opts: Options.Data) = JSONObject().apply {
        val rules = JSONArray()
        rules.put(JSONObject().put("action", "sniff"))
        rules.put(JSONObject().apply { put("protocol", "dns"); put("action", "hijack-dns") })

        if (opts.region != "other" && opts.region.isNotBlank()) {
            rules.put(JSONObject().apply {
                put("domain_suffix", JSONArray().put(".${opts.region}")); put("outbound", "direct")
            })
            rules.put(JSONObject().apply {
                put("rule_set", JSONArray().put("geosite-${opts.region}").put("geoip-${opts.region}"))
                put("outbound", "direct")
            })
        }
        if (opts.bypassLan) {
            rules.put(JSONObject().apply { put("ip_is_private", true); put("outbound", "direct") })
        }
        if (opts.blockAds) {
            rules.put(JSONObject().apply {
                put("rule_set", JSONArray().apply { BLOCK_AD_SETS.forEach { put(it.first) } })
                put("action", "reject")
            })
        }

        put("rules", rules)
        val ruleSets = ruleSets(opts)
        if (ruleSets.length() > 0) put("rule_set", ruleSets)
        put("final", "proxy")
        put("auto_detect_interface", true)
        // Резолв доменов прокси-серверов — НАПРЯМУЮ, иначе deadlock (поднять proxy нужно
        // зарезолвив его домен, а dns-remote ходит через тот же proxy).
        put("default_domain_resolver", JSONObject().put("server", "dns-direct"))
    }

    /** rule_set'ы для региона + блокировки рекламы (remote .srs, скачиваются через proxy). */
    private fun ruleSets(opts: Options.Data) = JSONArray().apply {
        if (opts.region != "other" && opts.region.isNotBlank()) {
            put(remoteRuleSet("geoip-${opts.region}", "$GEO_BASE/country/geoip-${opts.region}.srs"))
            put(remoteRuleSet("geosite-${opts.region}", "$GEO_BASE/country/geosite-${opts.region}.srs"))
        }
        if (opts.blockAds) BLOCK_AD_SETS.forEach { put(remoteRuleSet(it.first, it.second)) }
    }

    private fun remoteRuleSet(tag: String, url: String) = JSONObject().apply {
        put("type", "remote"); put("tag", tag); put("format", "binary")
        put("url", url); put("update_interval", "120h"); put("download_detour", "proxy")
    }

    /**
     * Парсер DNS-адреса (порт parseDnsAddress): https://host/path → DoH; tls/tcp/udp/quic
     * → host[:port]; local/system → local; голый host → udp.
     */
    private fun parseDnsAddress(raw: String): JSONObject {
        val s = raw.trim()
        if (s.isEmpty() || s == "local" || s == "system") return JSONObject().put("type", "local")
        val m = Regex("^([a-zA-Z]+)://(.+)$").find(s)
            ?: return JSONObject().apply { put("type", "udp"); put("server", s) }
        val scheme = m.groupValues[1].lowercase()
        val rest = m.groupValues[2]
        if (scheme == "https") {
            val slash = rest.indexOf('/')
            val hostPart = if (slash >= 0) rest.substring(0, slash) else rest
            val path = if (slash >= 0) rest.substring(slash) else ""
            val o = JSONObject().put("type", "https")
            val colon = hostPart.lastIndexOf(':')
            if (colon > 0) {
                o.put("server", hostPart.substring(0, colon))
                hostPart.substring(colon + 1).toIntOrNull()?.let { o.put("server_port", it) }
            } else o.put("server", hostPart)
            if (path.isNotEmpty() && path != "/") o.put("path", path)
            return o
        }
        if (scheme in setOf("tls", "tcp", "udp", "quic")) {
            val o = JSONObject().put("type", scheme)
            val colon = rest.lastIndexOf(':')
            if (colon > 0 && !rest.contains('/')) {
                o.put("server", rest.substring(0, colon))
                rest.substring(colon + 1).toIntOrNull()?.let { o.put("server_port", it) }
            } else o.put("server", rest)
            return o
        }
        return JSONObject().apply { put("type", "udp"); put("server", s) }
    }

    // monitoring (IP-гео всех нод) намеренно НЕ включаем: на старте поднимал десятки
    // соединений через каждую ноду = шторм и батарея.
    private fun experimental() = JSONObject().apply {
        put("cache_file", JSONObject().apply { put("enabled", true); put("store_rdrc", true) })
        put("unified_delay", JSONObject().put("enabled", true))
    }

    // ── outbound по протоколу ──────────────────────────────────
    private fun outbound(n: Node, tag: String, opts: Options.Data): JSONObject {
        val o = JSONObject().apply { put("tag", tag); put("server", n.host); put("server_port", n.port) }
        when (n.proto) {
            "vmess" -> {
                o.put("type", "vmess"); o.put("uuid", n.uuid)
                o.put("security", n.cipher.ifBlank { "auto" }); o.put("alter_id", n.alterId)
                o.put("packet_encoding", "xudp")
                tls(n, opts)?.let { o.put("tls", it) }; transport(n)?.let { o.put("transport", it) }
            }
            "trojan" -> {
                o.put("type", "trojan"); o.put("password", n.password)
                tls(n, opts)?.let { o.put("tls", it) }; transport(n)?.let { o.put("transport", it) }
            }
            "shadowsocks" -> {
                o.put("type", "shadowsocks"); o.put("method", n.method); o.put("password", n.password)
            }
            "hysteria2" -> {
                o.put("type", "hysteria2"); o.put("password", n.password)
                if (n.upMbps > 0) o.put("up_mbps", n.upMbps)
                if (n.downMbps > 0) o.put("down_mbps", n.downMbps)
                if (n.obfs.isNotEmpty()) o.put("obfs", JSONObject().apply {
                    put("type", n.obfs); if (n.obfsPassword.isNotEmpty()) put("password", n.obfsPassword)
                })
                o.put("tls", quicTls(n))
            }
            "tuic" -> {
                o.put("type", "tuic"); o.put("uuid", n.uuid); o.put("password", n.password)
                o.put("congestion_control", n.congestion.ifBlank { "bbr" })
                o.put("udp_relay_mode", n.udpRelay.ifBlank { "native" })
                o.put("zero_rtt_handshake", n.zeroRtt)
                o.put("tls", quicTls(n))
            }
            else -> { // vless
                o.put("type", "vless"); o.put("uuid", n.uuid); o.put("packet_encoding", "xudp")
                if (n.flow.isNotEmpty()) o.put("flow", n.flow)
                tls(n, opts)?.let { o.put("tls", it) }; transport(n)?.let { o.put("transport", it) }
            }
        }
        applyMux(o, opts)
        return o
    }

    /**
     * TLS для vless/vmess/trojan (tls|reality) + опц. tls-tricks форка (fragment/padding/
     * mixedcase_sni). ВАЖНО: ключи строго как принимает форк 1.13 — иначе весь конфиг
     * падает «unknown field». Применяется только к TCP-TLS; QUIC идёт через quicTls.
     */
    private fun tls(n: Node, opts: Options.Data): JSONObject? {
        if (n.security != "tls" && n.security != "reality") return null
        return JSONObject().apply {
            put("enabled", true)
            put("server_name", n.sni.ifBlank { n.host })
            put("utls", JSONObject().apply { put("enabled", true); put("fingerprint", n.fp.ifBlank { "chrome" }) })
            if (n.alpn.isNotEmpty()) put("alpn", alpnArr(n.alpn))
            if (n.security == "reality") put("reality", JSONObject().apply {
                put("enabled", true); put("public_key", n.pbk); put("short_id", n.sid)
            })
            applyTlsTricks(this, opts)
        }
    }

    private fun applyTlsTricks(tls: JSONObject, opts: Options.Data) {
        if (opts.tlsFragment) {
            // fragment (TCP-сегменты) и record_fragment (TLS-записи) взаимоисключающие.
            if (opts.fragmentMode == "tcp") tls.put("fragment", true)
            else tls.put("record_fragment", true)
        }
        val tricks = JSONObject()
        if (opts.tlsPadding) tricks.put("padding_size", "${opts.paddingFrom}-${opts.paddingTo}")
        if (opts.mixedSniCase) tricks.put("mixedcase_sni", true)
        if (tricks.length() > 0) tls.put("tls_tricks", tricks)
    }

    private fun applyMux(out: JSONObject, opts: Options.Data) {
        if (!opts.muxEnable) return
        out.put("multiplex", JSONObject().apply {
            put("enabled", true)
            put("protocol", opts.muxProtocol)
            put("max_streams", opts.muxMaxStreams)
            put("padding", opts.muxPadding)
        })
    }

    /** TLS для hysteria2/tuic (всегда поверх QUIC) — без tls-tricks. */
    private fun quicTls(n: Node) = JSONObject().apply {
        put("enabled", true)
        put("server_name", n.sni.ifBlank { n.host })
        put("insecure", n.insecure)
        put("alpn", alpnArr(n.alpn.ifBlank { "h3" }))
    }

    private fun transport(n: Node): JSONObject? = when (n.type) {
        "ws" -> JSONObject().apply {
            put("type", "ws")
            if (n.path.isNotEmpty()) put("path", n.path)
            if (n.hostHeader.isNotEmpty()) put("headers", JSONObject().put("Host", n.hostHeader))
        }
        "grpc" -> JSONObject().apply {
            put("type", "grpc")
            if (n.serviceName.isNotEmpty()) put("service_name", n.serviceName)
        }
        "http", "h2" -> JSONObject().apply {
            put("type", "http")
            if (n.path.isNotEmpty()) put("path", n.path)
            if (n.hostHeader.isNotEmpty()) put("host", JSONArray().apply {
                n.hostHeader.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { put(it) }
            })
        }
        // xhttp НАТИВНО в форке hiddify-sing-box (transport/v2rayxhttp есть всегда, без
        // build-тега; тот же форк, что у hiddify-app, где эти ноды работают). extra={...}
        // из ссылки — в Xray-схеме; переводим в схему форка (downloadSettings: address→
        // server, realitySettings→tls.reality). Раньше уводили в standalone-xray по
        // ошибочному выводу «форк xhttp не тянет» — тянет, если транслировать extra.
        "xhttp" -> JSONObject().apply {
            put("type", "xhttp")
            if (n.path.isNotEmpty()) put("path", n.path)
            if (n.hostHeader.isNotEmpty()) put("host", n.hostHeader)
            if (n.mode.isNotEmpty()) put("mode", n.mode)
            if (n.extra.isNotBlank()) runCatching {
                mergeXhttpExtra(this, JSONObject(n.extra))
            }
            // форк падает на пустом mode ("mode is not set") → дефолт auto
            if (optString("mode").isEmpty()) put("mode", "auto")
        }
        else -> null
    }

    // xhttp base-ключи с одинаковыми именами в Xray и форке sing-box.
    private val XHTTP_PASS_KEYS = listOf(
        "host", "path", "headers", "xPaddingBytes", "noGRPCHeader", "noSSEHeader",
        "scMaxEachPostBytes", "scMinPostsIntervalMs", "scMaxBufferedPosts",
        "scStreamUpServerSecs", "xmux",
    )

    /** Мерж Xray-extra в xhttp-транспорт форка: только whitelisted-ключи (unknown field
     *  роняет ВЕСЬ конфиг) + трансляция downloadSettings. Порт desktop singbox.js. */
    private fun mergeXhttpExtra(t: JSONObject, ex: JSONObject) {
        for (k in XHTTP_PASS_KEYS) if (ex.has(k)) t.put(k, ex.get(k))
        ex.optString("mode").takeIf { it.isNotEmpty() }?.let { t.put("mode", it) }
        ex.optJSONObject("downloadSettings")?.let { ds ->
            xrayDownloadToSingbox(ds)?.let { t.put("downloadSettings", it) }
        }
    }

    /** Xray downloadSettings (StreamSettings) → V2RayXHTTPDownloadOptions форка:
     *  address→server, port→server_port, xhttpSettings.* → плоские base-поля, tls. */
    private fun xrayDownloadToSingbox(ds: JSONObject): JSONObject? {
        val d = JSONObject()
        ds.optString("address").takeIf { it.isNotEmpty() }?.let { d.put("server", it) }
        if (ds.has("port")) d.put("server_port", ds.optInt("port"))
        val xs = ds.optJSONObject("xhttpSettings") ?: JSONObject()
        for (k in XHTTP_PASS_KEYS) if (xs.has(k)) d.put(k, xs.get(k))
        // под-опции download часто вложены в xhttpSettings.extra (как в ссылке) — тоже мержим
        xs.optJSONObject("extra")?.let { dex -> for (k in XHTTP_PASS_KEYS) if (dex.has(k)) d.put(k, dex.get(k)) }
        xrayTlsToSingbox(ds)?.let { d.put("tls", it) }
        return if (d.length() > 0) d else null
    }

    /** Xray tlsSettings/realitySettings → OutboundTLSOptions форка. */
    private fun xrayTlsToSingbox(ds: JSONObject): JSONObject? {
        val sec = ds.optString("security")
        if (sec != "tls" && sec != "reality") return null
        val ts = ds.optJSONObject("tlsSettings") ?: ds.optJSONObject("realitySettings") ?: JSONObject()
        return JSONObject().apply {
            put("enabled", true)
            ts.optString("serverName").takeIf { it.isNotEmpty() }?.let { put("server_name", it) }
            put("utls", JSONObject().put("enabled", true).put("fingerprint", ts.optString("fingerprint").ifBlank { "chrome" }))
            when (val alpn = ts.opt("alpn")) {
                is JSONArray -> if (alpn.length() > 0) put("alpn", alpn)
                is String -> if (alpn.isNotEmpty()) put("alpn", alpnArr(alpn))
            }
            if (ts.optBoolean("allowInsecure") || ts.optBoolean("insecure")) put("insecure", true)
            if (sec == "reality") put("reality", JSONObject().apply {
                put("enabled", true)
                put("public_key", ts.optString("publicKey").ifBlank { ts.optString("public_key") })
                put("short_id", ts.optString("shortId").ifBlank { ts.optString("short_id") })
            })
        }
    }

    private fun alpnArr(alpn: String) = JSONArray().apply {
        alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { put(it) }
    }

    /**
     * Clash-tag ноды — index-независимый, чисто по идентичности ноды. Тот же расчёт
     * зовёт UI, чтобы сопоставить ноду с её URLTestDelay из group-стрима.
     */
    fun tagOf(n: Node): String = tagOfId(n.id)

    fun tagOfId(id: String): String = "n$id"

    // ── xray-мост для xhttp (ОТКЛЮЧЁН) ───────────────────────────
    // xhttp теперь строится нативным outbound форка hiddify-sing-box (transport=xhttp,
    // см. transport()), как у hiddify-app — standalone-xray больше не нужен. Возвращаем
    // пустой список: XrayController.start при пустых мостах гасит xray и выходит, build()
    // строит все ноды нативно. Код XrayController оставлен на случай отката.
    const val XRAY_BASE_PORT = 31100

    data class XrayBridge(val node: Node, val tag: String, val port: Int)

    fun xrayBridges(nodes: List<Node>): List<XrayBridge> = emptyList()
}
