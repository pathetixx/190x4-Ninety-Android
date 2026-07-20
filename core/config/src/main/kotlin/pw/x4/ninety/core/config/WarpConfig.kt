package pw.x4.ninety.core.config

import java.util.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pw.x4.ninety.core.model.WarpMode
import pw.x4.ninety.core.model.WarpNoisePreset
import pw.x4.ninety.core.model.WarpRegistration
import pw.x4.ninety.core.model.WarpRegistrationSanitizer
import pw.x4.ninety.core.model.WarpSettings

data class WarpConfig(
    val settings: WarpSettings,
    val registration: WarpRegistration,
)

internal object WarpEndpointBuilder {
    fun build(config: WarpConfig?): JsonObject? {
        if (config == null || !config.settings.enabled) return null
        val settings = config.settings.normalized()
        val registration = WarpRegistrationSanitizer.sanitize(config.registration).registration ?: return null
        val endpoint = parseEndpoint(settings.endpoint) ?: return null
        val reserved = Base64.getDecoder().decode(registration.clientId).take(3)
        val addresses = buildList {
            if (registration.localIpv4.isNotEmpty()) add("${registration.localIpv4}/32")
            if (registration.localIpv6.isNotEmpty()) add("${registration.localIpv6}/128")
        }
        if (addresses.isEmpty()) return null

        return buildJsonObject {
            put("type", "wireguard")
            put("tag", TAG)
            put("address", JsonArray(addresses.map(::JsonPrimitive)))
            put("private_key", registration.privateKey)
            put("mtu", settings.mtu)
            put("peers", buildJsonArray {
                add(buildJsonObject {
                    put("address", endpoint.host)
                    put("port", endpoint.port)
                    put("public_key", registration.peerPublicKey)
                    put("allowed_ips", JsonArray(listOf(JsonPrimitive("0.0.0.0/0"), JsonPrimitive("::/0"))))
                    put("reserved", JsonArray(reserved.map { JsonPrimitive(it.toInt() and 0xff) }))
                })
            })
            noise(settings)?.let { put("noise", buildJsonObject { put("fake_packet", it) }) }
            if (settings.mode == WarpMode.CHAIN) put("detour", PROXY_TAG)
        }
    }

    private fun noise(settings: WarpSettings): JsonObject? = when (settings.noisePreset) {
        WarpNoisePreset.OFF -> null
        WarpNoisePreset.DEFAULT -> fakePacket("1-3", "10-30", "10-30")
        WarpNoisePreset.AGGRESSIVE -> fakePacket("3-8", "30-90", "5-15")
        WarpNoisePreset.CUSTOM -> fakePacket(
            settings.customCount.wireValue(1, 64),
            settings.customSize.wireValue(1, 1500),
            settings.customDelay.wireValue(0, 5000),
        )
    }

    private fun fakePacket(count: String, size: String, delay: String): JsonObject = buildJsonObject {
        put("enabled", true)
        put("count", count)
        put("size", size)
        put("delay", delay)
        put("mode", "random")
    }

    private fun parseEndpoint(value: String): Endpoint? {
        val source = value.trim().ifBlank { WarpSettings.DEFAULT_ENDPOINT }
        if (source in setOf("auto", "auto4", "auto6")) return Endpoint(source, DEFAULT_PORT)
        if (source.startsWith('[')) {
            val closing = source.indexOf(']')
            if (closing <= 1) return null
            val host = source.substring(1, closing)
            val port = source.substring(closing + 1).removePrefix(":").toIntOrNull() ?: DEFAULT_PORT
            return Endpoint(host, port).takeIf(Endpoint::valid)
        }
        val separator = source.lastIndexOf(':')
        val hasSingleColon = separator > 0 && source.indexOf(':') == separator
        val host = if (hasSingleColon) source.substring(0, separator) else source
        val port = if (hasSingleColon) source.substring(separator + 1).toIntOrNull() ?: return null else DEFAULT_PORT
        return Endpoint(host, port).takeIf(Endpoint::valid)
    }

    private data class Endpoint(val host: String, val port: Int) {
        fun valid(): Boolean = host.isNotBlank() && host.none(Char::isWhitespace) && port in 1..65535
    }

    const val TAG = "warp"
    private const val PROXY_TAG = "proxy"
    private const val DEFAULT_PORT = 2408
}
