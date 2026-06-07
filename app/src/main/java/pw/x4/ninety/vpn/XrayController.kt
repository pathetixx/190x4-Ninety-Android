package pw.x4.ninety.vpn

import android.net.VpnService
import io.nekohasekai.xraybridge.Protector
import io.nekohasekai.xraybridge.Xraybridge
import org.json.JSONArray
import org.json.JSONObject
import pw.x4.ninety.data.Node

/**
 * Локальный xray для xhttp-нод (two-core, порт desktop singbox.js nodeToXray*).
 * Форк sing-box xhttp «рассыпается» (только пинг) → xhttp-ноды поднимаем в xray:
 * на каждую — socks-inbound (127.0.0.1:порт) + vless/trojan-outbound с xhttp-stream;
 * sing-box ходит к этим портам обычным socks-outbound (см. ConfigBuilder.socksOutbound).
 * Ядро xray вшито в libbox.aar (xraybridge), один Go-рантайм.
 */
object XrayController {

    @Volatile private var running = false

    /**
     * Поднять xray под xhttp-ноды профиля. Идемпотентно (пересобирает). Если xhttp-нод
     * нет — гасит xray и возвращает false (xray не нужен). protect через [service],
     * иначе сокеты xray уйдут в TUN sing-box (петля).
     */
    fun start(nodes: List<Node>, service: VpnService): Boolean {
        val bridges = ConfigBuilder.xrayBridges(nodes)
        if (bridges.isEmpty()) { stop(); return false }
        stop()
        try {
            Xraybridge.registerDialerController(object : Protector {
                // gomobile биндит Go `int` как Java `long` → fd:Long (VpnService.protect ждёт Int).
                override fun protect(fd: Long): Boolean = service.protect(fd.toInt())
            })
        } catch (_: Throwable) {}
        Xraybridge.runXrayFromJSON(buildConfig(bridges)) // бросит при ошибке конфига
        running = true
        return true
    }

    fun stop() {
        running = false
        try { Xraybridge.stopXray() } catch (_: Throwable) {}
    }

    fun isRunning(): Boolean = try { Xraybridge.getXrayState() } catch (_: Throwable) { false }

    private fun buildConfig(bridges: List<ConfigBuilder.XrayBridge>): String {
        val inbounds = JSONArray()
        val outbounds = JSONArray()
        val rules = JSONArray()
        bridges.forEach { b ->
            val inTag = "in_${b.node.id}"
            val outTag = "out_${b.node.id}"
            inbounds.put(JSONObject().apply {
                put("tag", inTag); put("listen", "127.0.0.1"); put("port", b.port)
                put("protocol", "socks")
                put("settings", JSONObject().apply { put("auth", "noauth"); put("udp", true) })
            })
            outbounds.put(xrayOutbound(b.node, outTag))
            rules.put(JSONObject().apply {
                put("type", "field"); put("inboundTag", JSONArray().put(inTag)); put("outboundTag", outTag)
            })
        }
        return JSONObject().apply {
            put("log", JSONObject().put("loglevel", "warning"))
            put("inbounds", inbounds)
            put("outbounds", outbounds)
            put("routing", JSONObject().put("rules", rules))
        }.toString()
    }

    private fun xrayOutbound(n: Node, tag: String): JSONObject = if (n.proto == "trojan") {
        JSONObject().apply {
            put("tag", tag); put("protocol", "trojan")
            put("settings", JSONObject().put("servers", JSONArray().put(JSONObject().apply {
                put("address", n.host); put("port", n.port); put("password", n.password)
            })))
            put("streamSettings", xrayStream(n))
        }
    } else {
        JSONObject().apply {
            put("tag", tag); put("protocol", "vless")
            val user = JSONObject().apply {
                put("id", n.uuid); put("encryption", "none")
                if (n.flow.isNotEmpty()) put("flow", n.flow)
            }
            put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject().apply {
                put("address", n.host); put("port", n.port)
                put("users", JSONArray().put(user))
            })))
            put("streamSettings", xrayStream(n))
        }
    }

    /** streamSettings xray: network=xhttp + security(reality/tls/none) + xhttpSettings. */
    private fun xrayStream(n: Node): JSONObject {
        val ss = JSONObject().put("network", "xhttp")
        when (n.security) {
            "reality" -> {
                ss.put("security", "reality")
                ss.put("realitySettings", JSONObject().apply {
                    put("serverName", n.sni); put("fingerprint", n.fp.ifBlank { "chrome" })
                    put("publicKey", n.pbk); put("shortId", n.sid)
                })
            }
            "tls" -> {
                ss.put("security", "tls")
                ss.put("tlsSettings", JSONObject().apply {
                    put("serverName", n.sni); put("fingerprint", n.fp.ifBlank { "chrome" })
                    if (n.alpn.isNotEmpty()) put("alpn", JSONArray().apply {
                        n.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { put(it) }
                    })
                })
            }
            else -> ss.put("security", "none")
        }
        ss.put("xhttpSettings", JSONObject().apply {
            put("host", n.hostHeader.ifBlank { n.sni })
            put("path", n.path.ifBlank { "/" })
            put("mode", n.mode.ifBlank { "auto" })
        })
        return ss
    }
}
