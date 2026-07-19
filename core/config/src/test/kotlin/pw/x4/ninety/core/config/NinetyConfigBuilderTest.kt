package pw.x4.ninety.core.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pw.x4.ninety.core.model.DomainMatch
import pw.x4.ninety.core.model.ProxySelection
import pw.x4.ninety.core.model.RoutingRule
import pw.x4.ninety.core.model.RoutingRuleAction
import pw.x4.ninety.core.model.RoutingRuleType
import pw.x4.ninety.core.parser.ProxyFixtures
import pw.x4.ninety.core.parser.ProxyLinkParser

class NinetyConfigBuilderTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun `Android rules are normalized ordered and mapped to sing-box`() {
        val options = SingBoxOptions(
            region = "ru",
            bypassLan = true,
            customRules = listOf(
                RoutingRule(
                    id = "telegram",
                    type = RoutingRuleType.ANDROID_PACKAGE,
                    values = listOf("org.telegram.messenger"),
                    action = RoutingRuleAction.PROXY,
                ),
                RoutingRule(
                    id = "domain",
                    type = RoutingRuleType.DOMAIN,
                    match = DomainMatch.EXACT,
                    values = listOf("HTTPS://Example.COM/path"),
                    action = RoutingRuleAction.DIRECT,
                ),
                RoutingRule(
                    id = "ip",
                    type = RoutingRuleType.IP,
                    values = listOf("1.1.1.1"),
                    action = RoutingRuleAction.BLOCK,
                ),
            ),
        )

        val root = json.parseToJsonElement(
            NinetyConfigBuilder.build(listOf(configNode()), ProxySelection.Auto, options = options),
        ).jsonObject
        val rules = root.getValue("route").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }

        assertEquals("sniff", rules[0].getValue("action").jsonPrimitive.content)
        assertEquals("hijack-dns", rules[1].getValue("action").jsonPrimitive.content)
        assertEquals("org.telegram.messenger", rules[2].getValue("package_name").jsonArray.single().jsonPrimitive.content)
        assertEquals("proxy", rules[2].getValue("outbound").jsonPrimitive.content)
        assertEquals("example.com", rules[3].getValue("domain").jsonArray.single().jsonPrimitive.content)
        assertEquals("direct", rules[3].getValue("outbound").jsonPrimitive.content)
        assertEquals("1.1.1.1/32", rules[4].getValue("ip_cidr").jsonArray.single().jsonPrimitive.content)
        assertEquals("reject", rules[4].getValue("action").jsonPrimitive.content)
        assertTrue(rules.drop(5).any { it.containsKey("ip_is_private") })
        assertTrue(rules.drop(5).any { it.containsKey("rule_set") })
    }

    @Test
    fun `unsupported disabled and empty rules are omitted`() {
        val rules = listOf(
            RoutingRule(
                id = "process-on-android",
                type = RoutingRuleType.PROCESS_NAME,
                values = listOf("Telegram.exe"),
            ),
            RoutingRule(
                id = "disabled",
                enabled = false,
                type = RoutingRuleType.DOMAIN,
                values = listOf("disabled.example"),
            ),
            RoutingRule(
                id = "invalid",
                type = RoutingRuleType.IP,
                values = listOf("999.1.1.1"),
            ),
        )

        val base = SingBoxConfigBuilder.build(listOf(configNode()))
        val actual = NinetyConfigBuilder.build(
            listOf(configNode()),
            options = SingBoxOptions(customRules = rules, routingPlatform = RoutingPlatform.ANDROID),
        )

        assertEquals(base, actual)
    }

    @Test
    fun `desktop emits process and skips Android package`() {
        val root = json.parseToJsonElement(
            NinetyConfigBuilder.build(
                listOf(configNode()),
                options = SingBoxOptions(
                    routingPlatform = RoutingPlatform.DESKTOP,
                    customRules = listOf(
                        RoutingRule("process", type = RoutingRuleType.PROCESS_NAME, values = listOf("Telegram")),
                        RoutingRule("package", type = RoutingRuleType.ANDROID_PACKAGE, values = listOf("org.telegram.messenger")),
                    ),
                ),
            ),
        ).jsonObject
        val routeRules = root.getValue("route").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }

        assertTrue(routeRules.any { it.containsKey("process_name") })
        assertFalse(routeRules.any { it.containsKey("package_name") })
    }

    @Test
    fun `custom routing decoration remains byte deterministic`() {
        val options = SingBoxOptions(
            customRules = listOf(
                RoutingRule("one", type = RoutingRuleType.DOMAIN, values = listOf("example.com")),
            ),
        )
        val first = NinetyConfigBuilder.build(listOf(configNode()), options = options)
        val second = NinetyConfigBuilder.build(listOf(configNode()), options = options)
        assertEquals(first, second)
    }

    private fun configNode(): ConfigNode {
        val fixture = ProxyFixtures.all.first { it.id == "vless" }
        return ConfigNode("vless", requireNotNull(ProxyLinkParser.parseLink(fixture.link)))
    }
}
