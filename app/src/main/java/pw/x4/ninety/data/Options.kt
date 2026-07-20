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

object Options {
    data class Data(
        val testUrl: String = "https://www.gstatic.com/generate_204",
        val testIntervalSec: Int = 600,
        val logLevel: String = "info",
        val logDisabled: Boolean = false,
        val qualityEnabled: Boolean = true,
        val qualityMinDwellSec: Int = 90,
        val qualitySwitchMargin: Int = 8,
        val qualityFailureThreshold: Int = 2,
        val qualityCooldownSec: Int = 60,
        val region: String = "other",
        val blockAds: Boolean = false,
        val bypassLan: Boolean = true,
        val ipv6Mode: String = "disable",
        val customRules: List<RoutingRule> = emptyList(),
        val warpEnabled: Boolean = false,
        val warpMode: String = "direct",
        val warpEndpoint: String = "engage.cloudflareclient.com:2408",
        val warpMtu: Int = 1280,
        val warpNoisePreset: String = "off",
        val warpCountFrom: Int = 2,
        val warpCountTo: Int = 5,
        val warpSizeFrom: Int = 20,
        val warpSizeTo: Int = 60,
        val warpDelayFrom: Int = 8,
        val warpDelayTo: Int = 20,
        val dnsRemote: String = "https://1.1.1.1/dns-query",
        val dnsDirect: String = "udp://77.88.8.8",
        val fakeDns: Boolean = false,
        val independentCache: Boolean = false,
        val mtu: Int = 9000,
        val tunStack: String = "mixed",
        val strictRoute: Boolean = false,
        val tlsFragment: Boolean = false,
        val fragmentMode: String = "record",
        val mixedSniCase: Boolean = false,
        val tlsPadding: Boolean = false,
        val paddingFrom: Int = 100,
        val paddingTo: Int = 900,
        val muxEnable: Boolean = false,
        val muxProtocol: String = "h2mux",
        val muxMaxStreams: Int = 8,
        val muxPadding: Boolean = false,
    )

    var data by mutableStateOf(Data())
        private set
    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        Prefs.get(context).optionsJson?.let { raw -> runCatching { data = fromJson(JSONObject(raw)) } }
        loaded = true
    }

    fun update(context: Context, transform: (Data) -> Data) {
        val transformed = transform(data)
        val next = transformed.copy(
            customRules = transformed.customRules.mapNotNull(::sanitizeRule),
            warpMode = transformed.warpMode.takeIf { it in setOf("direct", "chain") } ?: "direct",
            warpEndpoint = transformed.warpEndpoint.trim().ifBlank { "engage.cloudflareclient.com:2408" },
            warpMtu = transformed.warpMtu.coerceIn(576, 1500),
            warpNoisePreset = transformed.warpNoisePreset.takeIf { it in setOf("off", "default", "aggressive", "custom") } ?: "off",
        )
        data = next
        Prefs.get(context).optionsJson = toJson(next).toString()
    }

    private fun toJson(d: Data) = JSONObject().apply {
        put("testUrl", d.testUrl); put("testIntervalSec", d.testIntervalSec)
        put("logLevel", d.logLevel); put("logDisabled", d.logDisabled)
        put("qualityEnabled", d.qualityEnabled); put("qualityMinDwellSec", d.qualityMinDwellSec)
        put("qualitySwitchMargin", d.qualitySwitchMargin); put("qualityFailureThreshold", d.qualityFailureThreshold)
        put("qualityCooldownSec", d.qualityCooldownSec)
        put("region", d.region); put("blockAds", d.blockAds); put("bypassLan", d.bypassLan); put("ipv6Mode", d.ipv6Mode)
        put("customRules", JSONArray().apply { d.customRules.forEach { put(ruleToJson(it)) } })
        put("warpEnabled", d.warpEnabled); put("warpMode", d.warpMode); put("warpEndpoint", d.warpEndpoint)
        put("warpMtu", d.warpMtu); put("warpNoisePreset", d.warpNoisePreset)
        put("warpCountFrom", d.warpCountFrom); put("warpCountTo", d.warpCountTo)
        put("warpSizeFrom", d.warpSizeFrom); put("warpSizeTo", d.warpSizeTo)
        put("warpDelayFrom", d.warpDelayFrom); put("warpDelayTo", d.warpDelayTo)
        put("dnsRemote", d.dnsRemote); put("dnsDirect", d.dnsDirect); put("fakeDns", d.fakeDns); put("independentCache", d.independentCache)
        put("mtu", d.mtu); put("tunStack", d.tunStack); put("strictRoute", d.strictRoute)
        put("tlsFragment", d.tlsFragment); put("fragmentMode", d.fragmentMode); put("mixedSniCase", d.mixedSniCase)
        put("tlsPadding", d.tlsPadding); put("paddingFrom", d.paddingFrom); put("paddingTo", d.paddingTo)
        put("muxEnable", d.muxEnable); put("muxProtocol", d.muxProtocol); put("muxMaxStreams", d.muxMaxStreams); put("muxPadding", d.muxPadding)
    }

    private fun fromJson(o: JSONObject): Data {
        val d = Data()
        return Data(
            testUrl=o.optString("testUrl",d.testUrl), testIntervalSec=o.optInt("testIntervalSec",d.testIntervalSec),
            logLevel=o.optString("logLevel",d.logLevel), logDisabled=o.optBoolean("logDisabled",d.logDisabled),
            qualityEnabled=o.optBoolean("qualityEnabled",d.qualityEnabled), qualityMinDwellSec=o.optInt("qualityMinDwellSec",d.qualityMinDwellSec),
            qualitySwitchMargin=o.optInt("qualitySwitchMargin",d.qualitySwitchMargin), qualityFailureThreshold=o.optInt("qualityFailureThreshold",d.qualityFailureThreshold),
            qualityCooldownSec=o.optInt("qualityCooldownSec",d.qualityCooldownSec), region=o.optString("region",d.region),
            blockAds=o.optBoolean("blockAds",d.blockAds), bypassLan=o.optBoolean("bypassLan",d.bypassLan), ipv6Mode=o.optString("ipv6Mode",d.ipv6Mode),
            customRules=parseRules(o.optJSONArray("customRules")), warpEnabled=o.optBoolean("warpEnabled",d.warpEnabled),
            warpMode=o.optString("warpMode",d.warpMode), warpEndpoint=o.optString("warpEndpoint",d.warpEndpoint), warpMtu=o.optInt("warpMtu",d.warpMtu),
            warpNoisePreset=o.optString("warpNoisePreset",d.warpNoisePreset), warpCountFrom=o.optInt("warpCountFrom",d.warpCountFrom), warpCountTo=o.optInt("warpCountTo",d.warpCountTo),
            warpSizeFrom=o.optInt("warpSizeFrom",d.warpSizeFrom), warpSizeTo=o.optInt("warpSizeTo",d.warpSizeTo), warpDelayFrom=o.optInt("warpDelayFrom",d.warpDelayFrom), warpDelayTo=o.optInt("warpDelayTo",d.warpDelayTo),
            dnsRemote=o.optString("dnsRemote",d.dnsRemote), dnsDirect=o.optString("dnsDirect",d.dnsDirect), fakeDns=o.optBoolean("fakeDns",d.fakeDns), independentCache=o.optBoolean("independentCache",d.independentCache),
            mtu=o.optInt("mtu",d.mtu), tunStack=o.optString("tunStack",d.tunStack), strictRoute=o.optBoolean("strictRoute",d.strictRoute),
            tlsFragment=o.optBoolean("tlsFragment",d.tlsFragment), fragmentMode=o.optString("fragmentMode",d.fragmentMode), mixedSniCase=o.optBoolean("mixedSniCase",d.mixedSniCase),
            tlsPadding=o.optBoolean("tlsPadding",d.tlsPadding), paddingFrom=o.optInt("paddingFrom",d.paddingFrom), paddingTo=o.optInt("paddingTo",d.paddingTo),
            muxEnable=o.optBoolean("muxEnable",d.muxEnable), muxProtocol=o.optString("muxProtocol",d.muxProtocol), muxMaxStreams=o.optInt("muxMaxStreams",d.muxMaxStreams), muxPadding=o.optBoolean("muxPadding",d.muxPadding),
        )
    }

    private fun parseRules(a: JSONArray?): List<RoutingRule> { if(a==null)return emptyList(); return buildList { for(i in 0 until minOf(a.length(),128)){ val s=a.optJSONObject(i)?:continue; val values=s.optJSONArray("values")?.let{v->buildList{for(j in 0 until minOf(v.length(),256))v.optString(j).takeIf(String::isNotBlank)?.let(::add)}}.orEmpty(); sanitizeRule(RoutingRule(s.optString("id").ifBlank{UUID.randomUUID().toString()},s.optBoolean("enabled",true),RoutingRuleType.fromWire(s.optString("type")),DomainMatch.fromWire(s.optString("match")),values,RoutingRuleAction.fromWire(s.optString("action"))))?.let(::add)}} }
    private fun ruleToJson(r: RoutingRule)=JSONObject().apply{put("id",r.id);put("enabled",r.enabled);put("type",r.type.wireName);put("match",r.match.wireName);put("values",JSONArray(r.values));put("action",r.action.wireName)}
    private fun sanitizeRule(r: RoutingRule): RoutingRule? { val w=if(r.id.isBlank())r.copy(id=UUID.randomUUID().toString())else r; return RoutingRuleSanitizer.sanitize(w).rule.takeIf{it.values.isNotEmpty()} }
}
