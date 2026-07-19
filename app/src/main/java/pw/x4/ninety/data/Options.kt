package pw.x4.ninety.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Стор настроек ядра (порт desktop options.js → подмножество, реализуемое на Android).
 *
 * ⚠️ ВСЕ дефолты подобраны байт-в-байт под ТЕКУЩИЙ рабочий конфиг [ConfigBuilder]:
 * кто ничего не меняет — получает идентичный конфиг, проверенный на железе. Любая
 * опция влияет на JSON ТОЛЬКО при отклонении от дефолта (OFF/дефолт = как было).
 * WARP не включён — на Android M2 нет инфраструктуры регистрации WG-устройства.
 *
 * Реактивность: [data] — mutableState, Settings-UI перерисовывается; ConfigBuilder
 * читает плоский снимок [Data] при сборке/reload. Персист идёт через Preferences DataStore,
 * а старый SharedPreferences JSON остаётся rollback-журналом на время миграции.
 */
object Options {

    data class Data(
        // — Общие —
        val testUrl: String = "https://www.gstatic.com/generate_204",
        val testIntervalSec: Int = 600,
        val logLevel: String = "info",           // trace|debug|info|warn|error
        val logDisabled: Boolean = false,
        // — Маршрутизация —
        val region: String = "other",            // other|ru|cn|ir|tr|by
        val blockAds: Boolean = false,
        val bypassLan: Boolean = true,           // текущий конфиг: ip_is_private → direct
        val ipv6Mode: String = "disable",        // disable|enable|prefer|only
        // — DNS —
        val dnsRemote: String = "https://1.1.1.1/dns-query",
        val dnsDirect: String = "udp://77.88.8.8",
        val fakeDns: Boolean = false,
        val independentCache: Boolean = false,   // текущий конфиг ключ не пишет → false
        // — Локальный доступ —
        val mtu: Int = 9000,
        val tunStack: String = "mixed",          // mixed|gvisor|system
        val strictRoute: Boolean = false,
        // — TLS-фрагментация (форк hiddify, per-outbound tls{}) —
        val tlsFragment: Boolean = false,
        val fragmentMode: String = "record",     // record|tcp
        val mixedSniCase: Boolean = false,
        val tlsPadding: Boolean = false,
        val paddingFrom: Int = 100,
        val paddingTo: Int = 900,
        // — Мультиплексор —
        val muxEnable: Boolean = false,
        val muxProtocol: String = "h2mux",       // h2mux|smux|yamux
        val muxMaxStreams: Int = 8,
        val muxPadding: Boolean = false,
    )

    var data by mutableStateOf(Data())
        private set

    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        Prefs.get(context).optionsJson?.let { raw ->
            runCatching { data = fromJson(JSONObject(raw)) }
        }
        loaded = true
    }

    /** Транзакционное обновление: меняем поле(я) и сразу персистим. */
    fun update(context: Context, transform: (Data) -> Data) {
        val next = transform(data)
        data = next
        Prefs.get(context).optionsJson = toJson(next).toString()
    }

    private fun toJson(d: Data) = JSONObject().apply {
        put("testUrl", d.testUrl); put("testIntervalSec", d.testIntervalSec)
        put("logLevel", d.logLevel); put("logDisabled", d.logDisabled)
        put("region", d.region); put("blockAds", d.blockAds)
        put("bypassLan", d.bypassLan); put("ipv6Mode", d.ipv6Mode)
        put("dnsRemote", d.dnsRemote); put("dnsDirect", d.dnsDirect)
        put("fakeDns", d.fakeDns); put("independentCache", d.independentCache)
        put("mtu", d.mtu); put("tunStack", d.tunStack); put("strictRoute", d.strictRoute)
        put("tlsFragment", d.tlsFragment); put("fragmentMode", d.fragmentMode)
        put("mixedSniCase", d.mixedSniCase); put("tlsPadding", d.tlsPadding)
        put("paddingFrom", d.paddingFrom); put("paddingTo", d.paddingTo)
        put("muxEnable", d.muxEnable); put("muxProtocol", d.muxProtocol)
        put("muxMaxStreams", d.muxMaxStreams); put("muxPadding", d.muxPadding)
    }

    private fun fromJson(o: JSONObject): Data {
        val def = Data()
        return Data(
            testUrl = o.optString("testUrl", def.testUrl),
            testIntervalSec = o.optInt("testIntervalSec", def.testIntervalSec),
            logLevel = o.optString("logLevel", def.logLevel),
            logDisabled = o.optBoolean("logDisabled", def.logDisabled),
            region = o.optString("region", def.region),
            blockAds = o.optBoolean("blockAds", def.blockAds),
            bypassLan = o.optBoolean("bypassLan", def.bypassLan),
            ipv6Mode = o.optString("ipv6Mode", def.ipv6Mode),
            dnsRemote = o.optString("dnsRemote", def.dnsRemote),
            dnsDirect = o.optString("dnsDirect", def.dnsDirect),
            fakeDns = o.optBoolean("fakeDns", def.fakeDns),
            independentCache = o.optBoolean("independentCache", def.independentCache),
            mtu = o.optInt("mtu", def.mtu),
            tunStack = o.optString("tunStack", def.tunStack),
            strictRoute = o.optBoolean("strictRoute", def.strictRoute),
            tlsFragment = o.optBoolean("tlsFragment", def.tlsFragment),
            fragmentMode = o.optString("fragmentMode", def.fragmentMode),
            mixedSniCase = o.optBoolean("mixedSniCase", def.mixedSniCase),
            tlsPadding = o.optBoolean("tlsPadding", def.tlsPadding),
            paddingFrom = o.optInt("paddingFrom", def.paddingFrom),
            paddingTo = o.optInt("paddingTo", def.paddingTo),
            muxEnable = o.optBoolean("muxEnable", def.muxEnable),
            muxProtocol = o.optString("muxProtocol", def.muxProtocol),
            muxMaxStreams = o.optInt("muxMaxStreams", def.muxMaxStreams),
            muxPadding = o.optBoolean("muxPadding", def.muxPadding),
        )
    }
}
