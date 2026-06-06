package pw.x4.ninety.vpn

import org.json.JSONArray
import org.json.JSONObject
import pw.x4.ninety.data.Node

/**
 * Генератор sing-box JSON под Android (порт singbox.js). Всегда tun-inbound
 * (фактический tun отдаёт libbox.OpenTun через VpnService). Один узел → outbound
 * "proxy"; несколько → selector "proxy" + balancer "auto" + urltest "lowest".
 * xhttp-узлы вызывающий код отсеивает заранее (нужен xray, M3).
 */
object ConfigBuilder {

    private const val TEST_URL = "https://www.gstatic.com/generate_204"
    private const val INTERVAL = "600s"

    fun build(nodes: List<Node>, selectedId: String?, logPath: String? = null): String {
        require(nodes.isNotEmpty()) { "пустой список нод" }
        val multi = nodes.size >= 2

        val nodeOutbounds = JSONArray()
        val nodeTags = ArrayList<String>()
        var selectedTag: String? = null
        nodes.forEachIndexed { i, n ->
            val tag = if (multi) tagFor(i, n) else "proxy"
            nodeTags.add(tag)
            if (n.id == selectedId) selectedTag = tag
            nodeOutbounds.put(outbound(n, tag))
        }

        val outbounds = JSONArray()
        if (multi) {
            val tagsArr = JSONArray().apply { nodeTags.forEach { put(it) } }
            // selector "proxy" = [auto, lowest, ...ноды], default = выбранная или auto
            outbounds.put(JSONObject().apply {
                put("type", "selector"); put("tag", "proxy")
                put("outbounds", JSONArray().apply {
                    put("auto"); put("lowest"); nodeTags.forEach { put(it) }
                })
                put("default", selectedTag?.takeIf { nodeTags.contains(it) } ?: "auto")
                put("interrupt_exist_connections", true)
            })
            // balancer "auto" — lowest-delay per-connection
            outbounds.put(JSONObject().apply {
                put("type", "balancer"); put("tag", "auto")
                put("outbounds", tagsArr)
                put("strategy", "lowest-delay")
                put("delay_acceptable_ratio", 2)
                put("interrupt_exist_connections", true)
            })
            // urltest "lowest" — health-checker (наполняет monitoring)
            outbounds.put(JSONObject().apply {
                put("type", "urltest"); put("tag", "lowest")
                put("outbounds", JSONArray().apply { nodeTags.forEach { put(it) } })
                put("url", TEST_URL); put("interval", INTERVAL); put("tolerance", 50)
                put("interrupt_exist_connections", false)
            })
        }
        for (i in 0 until nodeOutbounds.length()) outbounds.put(nodeOutbounds.get(i))
        outbounds.put(JSONObject().apply { put("type", "direct"); put("tag", "direct") })

        val config = JSONObject().apply {
            // level=debug временно для отладки; output=файл → ядро пишет лог само
            // (platform-callback в этой libbox логи не отдаёт). Вернуть info после.
            put("log", JSONObject().apply {
                put("level", "debug"); put("timestamp", true)
                if (logPath != null) put("output", logPath)
            })
            put("dns", dns())
            put("inbounds", JSONArray().put(tunInbound()))
            put("outbounds", outbounds)
            put("route", route())
            put("experimental", experimental(multi))
        }
        return config.toString()
    }

    private fun tunInbound() = JSONObject().apply {
        put("type", "tun"); put("tag", "tun-in")
        put("address", JSONArray().put("172.19.0.1/30"))
        put("mtu", 9000)
        put("auto_route", true)
        put("strict_route", false)
        put("stack", "mixed")
    }

    private fun dns() = JSONObject().apply {
        put("servers", JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "dns-remote"); put("type", "https"); put("server", "1.1.1.1")
                put("path", "/dns-query"); put("detour", "proxy"); put("domain_resolver", "dns-direct")
            })
            put(JSONObject().apply {
                // detour:direct ОБЯЗАТЕЛЕН — иначе «прямой» резолвер сам идёт через route.final=proxy
                put("tag", "dns-direct"); put("type", "udp"); put("server", "77.88.8.8")
                put("detour", "direct")
            })
        })
        // ipv4_only: не отдаём AAAA → апп не пробует IPv6 первым (у нод нет v6-outbound,
        // иначе v6-dial висит до таймаута ~30с и только потом фоллбэк на v4).
        put("strategy", "ipv4_only")
        put("final", "dns-remote")
    }

    private fun route() = JSONObject().apply {
        put("rules", JSONArray().apply {
            put(JSONObject().put("action", "sniff"))
            put(JSONObject().apply { put("protocol", "dns"); put("action", "hijack-dns") })
            put(JSONObject().apply { put("ip_is_private", true); put("outbound", "direct") })
        })
        put("final", "proxy")
        put("auto_detect_interface", true)
        // Резолв доменов в адресах прокси-серверов — НАПРЯМУЮ, иначе deadlock:
        // чтобы поднять proxy надо зарезолвить его домен, а dns-remote ходит через тот же proxy.
        put("default_domain_resolver", JSONObject().put("server", "dns-direct"))
    }

    private fun experimental(multi: Boolean) = JSONObject().apply {
        put("cache_file", JSONObject().apply { put("enabled", true); put("store_rdrc", true) })
        put("unified_delay", JSONObject().put("enabled", true))
        if (multi) {
            put("monitoring", JSONObject().apply {
                put("urls", JSONArray().apply {
                    put(TEST_URL); put("https://www.google.com/generate_204")
                    put("https://cp.cloudflare.com")
                })
                put("interval", INTERVAL); put("debounce_window", "500ms"); put("idle_timeout", "1800s")
            })
        }
    }

    // ── outbound по протоколу ──────────────────────────────────
    private fun outbound(n: Node, tag: String): JSONObject {
        val o = JSONObject().apply { put("tag", tag); put("server", n.host); put("server_port", n.port) }
        when (n.proto) {
            "vmess" -> {
                o.put("type", "vmess"); o.put("uuid", n.uuid)
                o.put("security", n.cipher.ifBlank { "auto" }); o.put("alter_id", n.alterId)
                o.put("packet_encoding", "xudp")
                tls(n)?.let { o.put("tls", it) }; transport(n)?.let { o.put("transport", it) }
            }
            "trojan" -> {
                o.put("type", "trojan"); o.put("password", n.password)
                tls(n)?.let { o.put("tls", it) }; transport(n)?.let { o.put("transport", it) }
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
                tls(n)?.let { o.put("tls", it) }; transport(n)?.let { o.put("transport", it) }
            }
        }
        return o
    }

    /** TLS для vless/vmess/trojan (tls|reality). */
    private fun tls(n: Node): JSONObject? {
        if (n.security != "tls" && n.security != "reality") return null
        return JSONObject().apply {
            put("enabled", true)
            put("server_name", n.sni.ifBlank { n.host })
            put("utls", JSONObject().apply { put("enabled", true); put("fingerprint", n.fp.ifBlank { "chrome" }) })
            if (n.alpn.isNotEmpty()) put("alpn", alpnArr(n.alpn))
            if (n.security == "reality") put("reality", JSONObject().apply {
                put("enabled", true); put("public_key", n.pbk); put("short_id", n.sid)
            })
        }
    }

    /** TLS для hysteria2/tuic (всегда поверх QUIC). */
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
        else -> null
    }

    private fun alpnArr(alpn: String) = JSONArray().apply {
        alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { put(it) }
    }

    private fun tagFor(i: Int, n: Node): String {
        val safe = n.name.replace("[^A-Za-z0-9_.-]".toRegex(), "-").take(20)
        return "n$i-$safe"
    }
}
