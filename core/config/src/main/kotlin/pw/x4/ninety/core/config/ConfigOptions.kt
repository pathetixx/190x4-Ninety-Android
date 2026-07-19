package pw.x4.ninety.core.config

import pw.x4.ninety.core.model.RoutingRule

enum class Ipv6Mode(val wireName: String) {
    DISABLE("disable"),
    ENABLE("enable"),
    PREFER("prefer"),
    ONLY("only");

    companion object {
        fun fromWire(value: String): Ipv6Mode = entries.firstOrNull { it.wireName == value } ?: DISABLE
    }
}

enum class TunStack(val wireName: String) {
    MIXED("mixed"),
    GVISOR("gvisor"),
    SYSTEM("system");

    companion object {
        fun fromWire(value: String): TunStack = entries.firstOrNull { it.wireName == value } ?: MIXED
    }
}

enum class FragmentMode(val wireName: String) {
    RECORD("record"),
    TCP("tcp");

    companion object {
        fun fromWire(value: String): FragmentMode = entries.firstOrNull { it.wireName == value } ?: RECORD
    }
}

enum class RoutingPlatform {
    ANDROID,
    DESKTOP,
}

/**
 * Platform-neutral subset of Ninety options used to build a sing-box config.
 * Android persistence and UI map into this immutable snapshot before every start/reload.
 */
data class SingBoxOptions(
    val testUrl: String = "https://www.gstatic.com/generate_204",
    val testIntervalSec: Int = 600,
    val logLevel: String = "info",
    val logDisabled: Boolean = false,
    val region: String = "other",
    val blockAds: Boolean = false,
    val bypassLan: Boolean = true,
    val ipv6Mode: Ipv6Mode = Ipv6Mode.DISABLE,
    val customRules: List<RoutingRule> = emptyList(),
    val routingPlatform: RoutingPlatform = RoutingPlatform.ANDROID,
    val dnsRemote: String = "https://1.1.1.1/dns-query",
    val dnsDirect: String = "udp://77.88.8.8",
    val fakeDns: Boolean = false,
    val independentCache: Boolean = false,
    val mtu: Int = 9000,
    val tunStack: TunStack = TunStack.MIXED,
    val strictRoute: Boolean = false,
    val tlsFragment: Boolean = false,
    val fragmentMode: FragmentMode = FragmentMode.RECORD,
    val fragmentFallbackDelay: String = "",
    val mixedSniCase: Boolean = false,
    val tlsPadding: Boolean = false,
    val paddingFrom: Int = 100,
    val paddingTo: Int = 900,
    val muxEnable: Boolean = false,
    val muxProtocol: String = "h2mux",
    val muxMaxStreams: Int = 8,
    val muxPadding: Boolean = false,
)
