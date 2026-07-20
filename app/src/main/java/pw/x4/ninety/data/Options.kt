package pw.x4.ninety.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import pw.x4.ninety.core.model.DomainMatch
import pw.x4.ninety.core.model.RoutingRule
import pw.x4.ninety.core.model.RoutingRuleAction
import pw.x4.ninety.core.model.RoutingRuleSanitizer
import pw.x4.ninety.core.model.RoutingRuleType

/**
 * Стор настроек ядра (порт desktop options.js → подмножество, реализуемое на Android).
 *
 * ⚠️ ВСЕ дефолты подобраны байт-в-байт под ТЕКУЩИЙ рабочий конфиг [ConfigBuilder]:
 * кто ничего не меняет — получает идентичный конфиг, проверенный на железе. Любая
 * опция влияет на JSON ТОЛЬКО при отклонении от дефолта (OFF/дефолт = как было).
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
        // — Quality Engine / устойчивый Auto —
        val qualityEnabled: Boolean = true,
        val qualityMinDwellSec: Int = 90,
        val qualitySwitchMargin: Int = 8,
        val qualityFailureThreshold: Int = 2,
        val qualityCooldownSec: Int = 60,
        // — Маршрутизация —
        val region: String = "other",            // other|ru|cn|ir|tr|by
        val blockAds: Boolean = false,
        val bypassLan: Boolean = true,
        val ipv6Mode: String = "disable",        // disable|enable|prefer|only
        val customRules: List<RoutingRule> = emptyList(),
        // — DNS —
        val dnsRemote: String = "https://1.1.1.1/dns-query",
        val dnsDirect: String = "udp://77.88.8.8",
        val fakeDns: Boolean = false,
        val independentCache: Boolean = false,
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

    /** Транзакционное обновление: меняем поле(я), нормализуем правила и сразу персистим. */
    fun update(context: Context, transform: (Data) -> Data) {
        val transformed = transform(data)
        val next = transformed.copy(customRules = transformed.customRules.mapNotNull(::sanitizeRule))
        data = next
        Prefs.get(context).optionsJson = toJson(next).toString()
    }

    private fun toJson(d: Data) = JSONObject().apply {
        put("testUrl", d.testUrl); put("testIntervalSec", d.testIntervalSec)
        put("logLevel", d.logLevel); put("logDisabled", d.logDisabled)
        put("qualityEnabled", d.qualityEnabled)
        put("qualityMinDwellSec", d.qualityMinDwellSec)
        put("qualitySwitchMargin", d.qualitySwitchMargin)
        put("qualityFailureThreshold", d.qualityFailureThreshold)
        put("qualityCooldownSec", d.qualityCooldownSec)
        put("region", d.region); put("blockAds", d.blockAds)
        put("bypassLan", d.bypassLan); put("ipv6Mode", d.ipv6Mode)
        put("customRules", JSONArray().apply { d.customRules.forEach { put(ruleToJson(it)) } })
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
            qualityEnabled = o.optBoolean("qualityEnabled", def.qualityEnabled),
            qualityMinDwellSec = o.optInt("qualityMinDwellSec", def.qualityMinDwellSec),
            qualitySwitchMargin = o.optInt("qualitySwitchMargin", def.qualitySwitchMargin),
            qualityFailureThreshold = o.optInt("qualityFailureThreshold", def.qualityFailureThreshold),
            qualityCooldownSec = o.optInt("qualityCooldownSec", def.qualityCooldownSec),
            region = o.optString("region", def.region),
            blockAds = o.optBoolean("blockAds", def.blockAds),
            bypassLan = o.optBoolean("bypassLan", def.bypassLan),
            ipv6Mode = o.optString("ipv6Mode", def.ipv6Mode),
            customRules = parseRules(o.optJSONArray("customRules")),
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

    private fun parseRules(array: JSONArray?): List<RoutingRule> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until minOf(array.length(), MAX_RULES)) {
                val source = array.optJSONObject(index) ?: continue
                val values = source.optJSONArray("values")?.let { rawValues ->
                    buildList {
                        for (valueIndex in 0 until minOf(rawValues.length(), MAX_VALUES_PER_RULE)) {
                            rawValues.optString(valueIndex).takeIf(String::isNotBlank)?.let(::add)
                        }
                    }
                }.orEmpty()
                sanitizeRule(
                    RoutingRule(
                        id = source.optString("id").ifBlank { UUID.randomUUID().toString() },
                        enabled = source.optBoolean("enabled", true),
                        type = RoutingRuleType.fromWire(source.optString("type")),
                        match = DomainMatch.fromWire(source.optString("match")),
                        values = values,
                        action = RoutingRuleAction.fromWire(source.optString("action")),
                    ),
                )?.let(::add)
            }
        }
    }

    private fun ruleToJson(rule: RoutingRule) = JSONObject().apply {
        put("id", rule.id)
        put("enabled", rule.enabled)
        put("type", rule.type.wireName)
        put("match", rule.match.wireName)
        put("values", JSONArray(rule.values))
        put("action", rule.action.wireName)
    }

    private fun sanitizeRule(rule: RoutingRule): RoutingRule? {
        val withId = if (rule.id.isBlank()) rule.copy(id = UUID.randomUUID().toString()) else rule
        val clean = RoutingRuleSanitizer.sanitize(withId).rule
        return clean.takeIf { it.values.isNotEmpty() }
    }

    private const val MAX_RULES = 128
    private const val MAX_VALUES_PER_RULE = 256
}
