package pw.x4.ninety.core.model

import java.net.Inet6Address
import java.net.InetAddress
import java.util.Base64

enum class WarpMode(val wireName: String) {
    DIRECT("direct"),
    CHAIN("chain");

    companion object {
        fun fromWire(value: String): WarpMode = entries.firstOrNull { it.wireName == value } ?: DIRECT
    }
}

enum class WarpNoisePreset(val wireName: String) {
    OFF("off"),
    DEFAULT("default"),
    AGGRESSIVE("aggressive"),
    CUSTOM("custom");

    companion object {
        fun fromWire(value: String): WarpNoisePreset = entries.firstOrNull { it.wireName == value } ?: OFF
    }
}

data class WarpRange(
    val from: Int,
    val to: Int,
) {
    fun normalized(min: Int, max: Int): WarpRange {
        val left = from.coerceIn(min, max)
        val right = to.coerceIn(min, max)
        return WarpRange(kotlin.math.min(left, right), kotlin.math.max(left, right))
    }

    fun wireValue(min: Int, max: Int): String = normalized(min, max).let { "${it.from}-${it.to}" }
}

data class WarpSettings(
    val enabled: Boolean = false,
    val mode: WarpMode = WarpMode.DIRECT,
    val endpoint: String = DEFAULT_ENDPOINT,
    val mtu: Int = 1280,
    val noisePreset: WarpNoisePreset = WarpNoisePreset.OFF,
    val customCount: WarpRange = WarpRange(2, 5),
    val customSize: WarpRange = WarpRange(20, 60),
    val customDelay: WarpRange = WarpRange(8, 20),
) {
    fun normalized(): WarpSettings = copy(
        endpoint = endpoint.trim().ifBlank { DEFAULT_ENDPOINT },
        mtu = mtu.coerceIn(576, 1500),
        customCount = customCount.normalized(1, 64),
        customSize = customSize.normalized(1, 1500),
        customDelay = customDelay.normalized(0, 5000),
    )

    companion object {
        const val DEFAULT_ENDPOINT = "engage.cloudflareclient.com:2408"
    }
}

data class WarpRegistration(
    val registrationId: String,
    val accountId: String,
    val accessToken: String,
    val privateKey: String,
    val peerPublicKey: String,
    val localIpv4: String,
    val localIpv6: String,
    val clientId: String,
    val license: String? = null,
    val warpPlus: Boolean = false,
    val accountType: String = "free",
    val registeredAt: String,
)

data class WarpRegistrationValidation(
    val registration: WarpRegistration?,
    val errors: List<String>,
) {
    val valid: Boolean get() = registration != null && errors.isEmpty()
}

object WarpRegistrationSanitizer {
    fun sanitize(source: WarpRegistration): WarpRegistrationValidation {
        val clean = source.copy(
            registrationId = source.registrationId.trim(),
            accountId = source.accountId.trim(),
            accessToken = source.accessToken.trim(),
            privateKey = source.privateKey.trim(),
            peerPublicKey = source.peerPublicKey.trim(),
            localIpv4 = source.localIpv4.trim().substringBefore('/'),
            localIpv6 = source.localIpv6.trim().substringBefore('/'),
            clientId = source.clientId.trim(),
            license = source.license?.trim()?.takeIf(String::isNotEmpty),
            accountType = source.accountType.trim().ifBlank { "free" },
            registeredAt = source.registeredAt.trim(),
        )
        val errors = buildList {
            if (clean.registrationId.isEmpty()) add("missing registration id")
            if (clean.accessToken.isEmpty()) add("missing access token")
            if (!isBase64Key(clean.privateKey)) add("invalid private key")
            if (!isBase64Key(clean.peerPublicKey)) add("invalid peer public key")
            if (clean.localIpv4.isEmpty() && clean.localIpv6.isEmpty()) add("missing interface address")
            if (clean.localIpv4.isNotEmpty() && !isIpv4(clean.localIpv4)) add("invalid IPv4 address")
            if (clean.localIpv6.isNotEmpty() && !isIpv6(clean.localIpv6)) add("invalid IPv6 address")
            if (!isClientId(clean.clientId)) add("invalid client id")
            if (clean.license != null && clean.license.length != LICENSE_LENGTH) add("invalid WARP+ license")
            if (clean.registeredAt.isEmpty()) add("missing registration time")
        }
        return WarpRegistrationValidation(clean.takeIf { errors.isEmpty() }, errors)
    }

    fun normalizeLicense(value: String?): String? {
        val clean = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        require(clean.length == LICENSE_LENGTH) { "WARP+ license key must be $LICENSE_LENGTH characters" }
        return clean
    }

    private fun isBase64Key(value: String): Boolean = runCatching {
        Base64.getDecoder().decode(value).size == KEY_BYTES
    }.getOrDefault(false)

    private fun isClientId(value: String): Boolean = runCatching {
        Base64.getDecoder().decode(value).size >= CLIENT_ID_BYTES
    }.getOrDefault(false)

    private fun isIpv4(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotEmpty() && part.length <= 3 &&
                (part == "0" || !part.startsWith('0')) &&
                part.toIntOrNull() in 0..255
        }
    }

    private fun isIpv6(value: String): Boolean {
        if (!value.contains(':')) return false
        return runCatching { InetAddress.getByName(value) is Inet6Address }.getOrDefault(false)
    }

    const val LICENSE_LENGTH = 26
    private const val KEY_BYTES = 32
    private const val CLIENT_ID_BYTES = 3
}
