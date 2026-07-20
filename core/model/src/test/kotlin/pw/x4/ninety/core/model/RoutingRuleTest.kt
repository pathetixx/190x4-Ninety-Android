package pw.x4.ninety.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoutingRuleTest {
    @Test
    fun `domain normalization strips URL wildcard path port and case`() {
        assertEquals(
            "youtube.com",
            RoutingRuleSanitizer.normalizeDomain(" HTTPS://*.YouTube.com:443/watch?v=1 "),
        )
        assertNull(RoutingRuleSanitizer.normalizeDomain("localhost"))
        assertNull(RoutingRuleSanitizer.normalizeDomain("https://bad host.example"))
    }

    @Test
    fun `IP normalization validates IPv4 IPv6 and prefixes`() {
        assertEquals("1.2.3.4/32", RoutingRuleSanitizer.normalizeIp("1.2.3.4"))
        assertEquals("10.0.0.0/8", RoutingRuleSanitizer.normalizeIp("10.0.0.0/8"))
        assertEquals("2001:db8::1/128", RoutingRuleSanitizer.normalizeIp("2001:db8::1"))
        assertEquals("2001:db8::/32", RoutingRuleSanitizer.normalizeIp("2001:db8::/32"))
        assertNull(RoutingRuleSanitizer.normalizeIp("256.1.1.1"))
        assertNull(RoutingRuleSanitizer.normalizeIp("01.2.3.4"))
        assertNull(RoutingRuleSanitizer.normalizeIp("2001:db8::/129"))
    }

    @Test
    fun `application identifiers are platform explicit`() {
        assertEquals(
            "org.telegram.messenger",
            RoutingRuleSanitizer.normalizePackage("package:org.telegram.messenger"),
        )
        assertNull(RoutingRuleSanitizer.normalizePackage("Telegram.exe"))
        assertEquals(
            "Telegram.exe",
            RoutingRuleSanitizer.normalizeProcess("C:\\Apps\\Telegram"),
        )
    }

    @Test
    fun `sanitizer drops invalid values and preserves priority order`() {
        val result = RoutingRuleSanitizer.sanitize(
            RoutingRule(
                id = " rule-1 ",
                type = RoutingRuleType.DOMAIN,
                match = DomainMatch.EXACT,
                values = listOf("Example.com", "bad value", "example.com", "api.example.com"),
                action = RoutingRuleAction.DIRECT,
            ),
        )

        assertEquals("rule-1", result.rule.id)
        assertEquals(DomainMatch.EXACT, result.rule.match)
        assertEquals(listOf("example.com", "api.example.com"), result.rule.values)
        assertEquals(1, result.dropped)
        assertEquals(1, result.duplicates)
    }

    @Test
    fun `non-domain rules discard irrelevant domain match`() {
        val result = RoutingRuleSanitizer.sanitize(
            RoutingRule(
                id = "ip",
                type = RoutingRuleType.IP,
                match = DomainMatch.KEYWORD,
                values = listOf("8.8.8.8"),
            ),
        )

        assertEquals(DomainMatch.SUFFIX, result.rule.match)
        assertEquals(listOf("8.8.8.8/32"), result.rule.values)
    }
}
