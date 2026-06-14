package pw.x4.ninety.vpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import pw.x4.ninety.data.Diag
import pw.x4.ninety.data.Node
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Локальный xray для xhttp-нод — standalone-бинарь в ОТДЕЛЬНОМ процессе (как xray.exe
 * на десктопе). Форк sing-box xhttp не тянет → на каждую xhttp-ноду xray поднимает
 * socks-inbound (127.0.0.1:порт) + vless/trojan-outbound с xhttp-stream; sing-box ходит
 * к этим портам обычным socks-outbound (см. ConfigBuilder.socksOutbound).
 *
 * Почему отдельный процесс, а не gomobile в libbox.aar: xray-core и hiddify-sing-box
 * тянут несовместимые версии общих зависимостей — слитые в один Go-бинарь, они роняли
 * рантайм на старте ЛЮБОЙ ноды (SIGABRT). Свой процесс = свой Go-рантайм, свои deps,
 * никакого конфликта; краш xray не валит приложение (просто exit-code).
 *
 * protect не нужен: VpnService исключает из TUN весь пакет (addDisallowedApplication),
 * значит сокеты xray-процесса (тот же package) идут мимо туннеля — петли нет.
 */
object XrayController {

    @Volatile private var proc: Process? = null

    /** Путь к standalone-бинарю xray (распакован из APK при extractNativeLibs=true). */
    private fun binPath(ctx: Context) = File(ctx.applicationInfo.nativeLibraryDir, "libxray.so")

    private fun configFile(ctx: Context) = File(ctx.filesDir, "xray-config.json")

    /**
     * Поднять xray под xhttp-ноды профиля. Идемпотентно (перезапускает). Если xhttp-нод
     * нет — гасит xray и возвращает false. Бинарь обязан существовать (CI кладёт в jniLibs);
     * если его нет (старый билд) — тихо false, обычные ноды не страдают.
     */
    @Synchronized
    fun start(nodes: List<Node>, ctx: Context): Boolean {
        val bridges = ConfigBuilder.xrayBridges(nodes)
        stop()
        if (bridges.isEmpty()) return false
        val bin = binPath(ctx)
        if (!bin.exists()) return false

        configFile(ctx).writeText(buildConfig(bridges))
        val pb = ProcessBuilder(bin.absolutePath, "run", "-c", configFile(ctx).absolutePath)
        pb.redirectErrorStream(true)
        pb.redirectOutput(Diag.xrayFile(ctx))           // лог xray → виден в Настройки → Логи
        pb.directory(ctx.filesDir)
        proc = pb.start()
        return true
    }

    @Synchronized
    fun stop() {
        try { proc?.destroy() } catch (_: Throwable) {}
        proc = null
    }

    fun isRunning(): Boolean = try { proc?.isAlive == true } catch (_: Throwable) { false }

    // ── пре-резолв хостов в IP ───────────────────────────────────
    // Go-резолвер ВНУТРИ xray-бинаря на Android бьёт в [::1]:53 (нет resolv.conf) и
    // sockopt.domainStrategy его НЕ перебивает в http2-дайлере download-канала xhttp
    // (проверено на железе: downloadSettings всё равно резолвит [::1]:53). Поэтому
    // резолвим хост сами — системным резолвером Android (для Java-кода он работает) —
    // и подставляем IP в address ДАЙЛА, оставляя hostname в SNI/Host. Тогда xray
    // никакой DNS не делает. Не зарезолвилось — отдаём host как есть (фолбэк на dns-блок).
    private val ipCache = HashMap<String, String>()
    private fun dialAddr(host: String): String {
        if (host.isBlank() || host.contains(':') || host.all { it.isDigit() || it == '.' }) return host
        ipCache[host]?.let { return it }
        val pool = Executors.newSingleThreadExecutor()
        val ip = runCatching {
            pool.submit<String?> {
                InetAddress.getAllByName(host).firstOrNull { it is Inet4Address }?.hostAddress
            }.get(4, TimeUnit.SECONDS)
        }.getOrNull()
        pool.shutdownNow()
        return (ip ?: host).also { ipCache[host] = it }
    }

    // ── конфиг xray ──────────────────────────────────────────────
    private fun buildConfig(bridges: List<ConfigBuilder.XrayBridge>): String {
        ipCache.clear()  // свежий резолв на каждую сессию — IP могли смениться
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
            put("log", JSONObject().put("loglevel", "info"))
            // Свой DNS: системный резолвер Go на Android указывает на [::1]:53 (его нет) →
            // xray не мог зарезолвить хост сервера (lookup … connection refused) и рвал
            // соединение. xray-процесс вне TUN → 1.1.1.1/8.8.8.8 резолвятся напрямую.
            // UseIPv4 — на телефоне IPv6 часто нет, AAAA-резолв впустую вешает dial.
            put("dns", JSONObject().apply {
                put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8"))
                put("queryStrategy", "UseIPv4")
            })
            put("inbounds", inbounds)
            put("outbounds", outbounds)
            put("routing", JSONObject().put("rules", rules))
        }.toString()
    }

    private fun xrayOutbound(n: Node, tag: String): JSONObject = if (n.proto == "trojan") {
        JSONObject().apply {
            put("tag", tag); put("protocol", "trojan")
            put("settings", JSONObject().put("servers", JSONArray().put(JSONObject().apply {
                put("address", dialAddr(n.host)); put("port", n.port); put("password", n.password)
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
                put("address", dialAddr(n.host)); put("port", n.port)
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
        val xs = JSONObject().apply {
            put("host", n.hostHeader.ifBlank { n.sni })
            put("path", n.path.ifBlank { "/" })
            put("mode", n.mode.ifBlank { "auto" })
            // extra={...} из ссылки — xhttp-подопции в Xray-схеме (downloadSettings,
            // xmux, xPaddingBytes, noGRPCHeader, headers…). Подмешиваем сырьём, как
            // десктоп (singbox.js Object.assign): без них сервер не отвечает → urltest timeout.
            if (n.extra.isNotBlank()) runCatching {
                val ex = JSONObject(n.extra)
                ex.keys().forEach { put(it, ex.get(it)) }
            }
        }
        // downloadSettings (split-режим xhttp) поднимает ОТДЕЛЬНЫЙ download-канал своим
        // http2-дайлером — он резолвит хост Go-резолвером даже при sockopt.domainStrategy
        // (на железе всё равно бил в [::1]:53). Поэтому подставляем IP в его address тоже,
        // как и в основной. serverName/host внутри остаются хостнеймом (SNI/Host).
        xs.optJSONObject("downloadSettings")?.let { ds ->
            ds.optString("address").takeIf { it.isNotBlank() }?.let { ds.put("address", dialAddr(it)) }
        }
        ss.put("xhttpSettings", xs)
        // Резолв адреса самого сервера через встроенный DNS xray, а не Go-резолвер:
        // на Android системный резолвер бьёт в [::1]:53 (его нет) → dial рвётся. dns-блок
        // (queryStrategy) трогает только проксируемые домены; адрес outbound-сервера —
        // только sockopt.domainStrategy. UseIPv4 — IPv6 на телефоне часто отсутствует.
        ss.put("sockopt", JSONObject().put("domainStrategy", "UseIPv4"))
        return ss
    }
}
