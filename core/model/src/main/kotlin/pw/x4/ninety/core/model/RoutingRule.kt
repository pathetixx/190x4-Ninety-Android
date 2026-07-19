package pw.x4.ninety.core.model

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress

/** Match subject supported by Ninety custom routing. */
enum class RoutingRuleType(val wireName: String) {
    DOMAIN("domain"),
    IP("ip"),
    ANDROID_PACKAGE("package"),
    PROCESS_NAME("process");

    companion object {
        fun fromWire(value: String): RoutingRuleType = entries.firstOrNull { it.wireName == value } ?: DOMAIN
    }
}

enum class DomainMatch(val wireName: String) {
    SUFFIX("suffix"),
    EXACT("exact"),
    KEYWORD("keyword");

    companion object {
        fun fromWire(value: String): DomainMatch = entries.firstOrNull { it.wireName == value } ?: SUFFIX
    }
}

enum class RoutingRuleAction(val wireName: String) {
    PROXY("proxy"),
    DIRECT("direct"),
    BLOCK("block");

    companion object {
        fun fromWire(value: String): RoutingRuleAction = entries.firstOrNull { it.wireName == value } ?: PROXY
    }
}

/**
 * Platform-neutral rule persisted by the app and consumed by the config builder.
 * Order in the containing list is priority: first matching rule wins.
 */
data class RoutingRule(
    val id: String,
    val enabled: Boolean = true,
    val type: RoutingRuleType = RoutingRuleType.DOMAIN,
    val match: DomainMatch = DomainMatch.SUFFIX,
    val values: List<String> = emptyList(),
    val action: RoutingRuleAction = RoutingRuleAction.PROXY,
)

data class SanitizedRoutingRule(
    val rule: RoutingRule,
    val dropped: Int,
    val duplicates: Int,
)

/** Shared validation used by persistence, UI and config generation. */
object RoutingRuleSanitizer {
    private val domainRegex = Regex(
        pattern = "^(?=.{1,253}$)([a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?\\.)+[a-z]{2,63}$",
        option = RegexOption.IGNORE_CASE,
    )
    private val packageRegex = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private val ipv4Regex = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    fun sanitize(rule: RoutingRule): SanitizedRoutingRule {
        val values = mutableListOf<String>()
        val seen = linkedSetOf<String>()
        var dropped = 0
        var duplicates = 0

        rule.values.forEach { raw ->
            val normalized = normalizeValue(rule.type, raw)
            if (normalized == null) {
                if (raw.isNotBlank()) dropped++
                return@forEach
            }
            if (!seen.add(normalized)) {
                duplicates++
                return@forEach
            }
            values += normalized
        }

        return SanitizedRoutingRule(
            rule = rule.copy(
                id = rule.id.trim(),
                match = if (rule.type == RoutingRuleType.DOMAIN) rule.match else DomainMatch.SUFFIX,
                values = values,
            ),
            dropped = dropped,
            duplicates = duplicates,
        )
    }

    fun normalizeValue(type: RoutingRuleType, raw: String): String? = when (type) {
        RoutingRuleType.DOMAIN -> normalizeDomain(raw)
        RoutingRuleType.IP -> normalizeIp(raw)
        RoutingRuleType.ANDROID_PACKAGE -> normalizePackage(raw)
        RoutingRuleType.PROCESS_NAME -> normalizeProcess(raw)
    }

    fun normalizeDomain(raw: String): String? {
        var value = raw.trim().lowercase()
        if (value.isEmpty()) return null

        value = value.replace(Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE), "")
        value = value.substringBefore('/').substringBefore('?').substringBefore('#')
        value = value.substringAfterLast('@')
        value = value.removePrefix("*.").removeSuffix(".")
        value = value.replace(Regex(":\\d+$"), "")
        if (value.isBlank() || value.contains(':')) return null

        val ascii = runCatching { IDN.toASCII(value) }.getOrNull()?.lowercase() ?: return null
        return ascii.takeIf(domainRegex::matches)
    }

    fun normalizeIp(raw: String): String? {
        val value = raw.trim().removePrefix("[").removeSuffix("]")
        if (value.isEmpty() || value.contains('%')) return null
        val parts = value.split('/')
        if (parts.size !in 1..2) return null

        val address = parts[0]
        val isV6 = address.contains(':')
        val validAddress = if (isV6) validIpv6(address) else validIpv4(address)
        if (!validAddress) return null

        val maxPrefix = if (isV6) 128 else 32
        val prefix = if (parts.size == 1) maxPrefix else parts[1].toIntOrNull() ?: return null
        if (prefix !in 0..maxPrefix) return null
        return "${address.lowercase()}/$prefix"
    }

    fun normalizePackage(raw: String): String? {
        val value = raw.trim().removePrefix("package:")
        return value.takeIf(packageRegex::matches)
    }

    fun normalizeProcess(raw: String): String? {
        var value = raw.trim().trimEnd('/', '\\')
        if (value.isEmpty()) return null
        value = value.substringAfterLast('/').substringAfterLast('\\')
        if (!value.endsWith(".exe", ignoreCase = true)) value += ".exe"
        return value.takeIf { it.length <= 255 && !it.any(Char::isWhitespace) }
    }

    private fun validIpv4(value: String): Boolean {
        val match = ipv4Regex.matchEntire(value) ?: return false
        return match.groupValues.drop(1).all { octet ->
            val number = octet.toIntOrNull() ?: return@all false
            number in 0..255 && number.toString() == octet.trimStart('0').ifEmpty { "0" }
        }
    }

    private fun validIpv6(value: String): Boolean = runCatching {
        value.contains(':') && InetAddress.getByName(value) is Inet6Address
    }.getOrDefault(false)
}
