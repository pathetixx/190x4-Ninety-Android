package pw.x4.ninety.core.config

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pw.x4.ninety.core.model.DomainMatch
import pw.x4.ninety.core.model.ProxySelection
import pw.x4.ninety.core.model.RoutingRule
import pw.x4.ninety.core.model.RoutingRuleAction
import pw.x4.ninety.core.model.RoutingRuleType
import pw.x4.ninety.core.model.WarpMode
import pw.x4.ninety.core.model.WarpNoisePreset
import pw.x4.ninety.core.model.WarpRange
import pw.x4.ninety.core.model.WarpRegistration
import pw.x4.ninety.core.model.WarpSettings
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
    fun `WARP direct becomes protected final DNS and custom proxy target`() {
        val root = json.parseToJsonElement(
            NinetyConfigBuilder.build(
                listOf(configNode()),
                options = SingBoxOptions(
                    warp = warpConfig(WarpMode.DIRECT),
                    customRules = listOf(
                        RoutingRule("protected", type = RoutingRuleType.DOMAIN, values = listOf("example.com")),
                    ),
                ),
            ),
        ).jsonObject
        val endpoint = root.getValue("endpoints").jsonArray.single().jsonObject
        val route = root.getValue("route").jsonObject
        val dnsRemote = root.getValue("dns").jsonObject.getValue("servers").jsonArray
            .map { it.jsonObject }
            .first { it.getValue("tag").jsonPrimitive.content == "dns-remote" }
        val protectedRule = route.getValue("rules").jsonArray[2].jsonObject

        assertEquals("wireguard", endpoint.getValue("type").jsonPrimitive.content)
        assertEquals("warp", endpoint.getValue("tag").jsonPrimitive.content)
        assertFalse(endpoint.containsKey("detour"))
        assertEquals("warp", route.getValue("final").jsonPrimitive.content)
        assertEquals("warp", dnsRemote.getValue("detour").jsonPrimitive.content)
        assertEquals("warp", protectedRule.getValue("outbound").jsonPrimitive.content)
        assertEquals(listOf(1, 2, 255), endpoint.getValue("peers").jsonArray.single().jsonObject
            .getValue("reserved").jsonArray.map { it.jsonPrimitive.content.toInt() })
    }

    @Test
    fun `WARP direct builds without any proxy nodes`() {
        val root = json.parseToJsonElement(
            NinetyConfigBuilder.build(
                nodes = emptyList(),
                selection = null,
                options = SingBoxOptions(warp = warpConfig(WarpMode.DIRECT)),
            ),
        ).jsonObject

        val outbounds = root.getValue("outbounds").jsonArray.map { it.jsonObject }
        assertEquals(listOf("direct"), outbounds.map { it.getValue("tag").jsonPrimitive.content })
        assertFalse(outbounds.any { it.getValue("tag").jsonPrimitive.content in setOf("proxy", "auto") })
        assertEquals("warp", root.getValue("route").jsonObject.getValue("final").jsonPrimitive.content)
        assertEquals("warp", root.getValue("endpoints").jsonArray.single().jsonObject.getValue("tag").jsonPrimitive.content)
    }

    @Test
    fun `empty proxy config without WARP fails clearly`() {
        val error = assertFailsWith<IllegalArgumentException> {
            NinetyConfigBuilder.build(nodes = emptyList(), selection = null)
        }
        assertTrue(error.message.orEmpty().contains("Ninety-нода"))
    }

    @Test
    fun `WARP chain without a proxy node fails clearly`() {
        val error = assertFailsWith<IllegalArgumentException> {
            NinetyConfigBuilder.build(
                nodes = emptyList(),
                selection = null,
                options = SingBoxOptions(warp = warpConfig(WarpMode.CHAIN)),
            )
        }
        assertTrue(error.message.orEmpty().contains("WARP Chain"))
    }

    @Test
    fun `WARP chain detours through selector and emits custom noise`() {
        val endpoint = json.parseToJsonElement(
            NinetyConfigBuilder.build(
                listOf(configNode()),
                options = SingBoxOptions(
                    warp = warpConfig(WarpMode.CHAIN).copy(
                        settings = WarpSettings(
                            enabled = true,
                            mode = WarpMode.CHAIN,
                            endpoint = "[2606:4700:d0::a29f:c001]:500",
                            mtu = 1400,
                            noisePreset = WarpNoisePreset.CUSTOM,
                            customCount = WarpRange(5, 2),
                            customSize = WarpRange(20, 40),
                            customDelay = WarpRange(1, 9),
                        ),
                    ),
                ),
            ),
        ).jsonObject.getValue("endpoints").jsonArray.single().jsonObject

        assertEquals("proxy", endpoint.getValue("detour").jsonPrimitive.content)
        assertEquals(1400, endpoint.getValue("mtu").jsonPrimitive.content.toInt())
        val peer = endpoint.getValue("peers").jsonArray.single().jsonObject
        assertEquals("2606:4700:d0::a29f:c001", peer.getValue("address").jsonPrimitive.content)
        assertEquals(500, peer.getValue("port").jsonPrimitive.content.toInt())
        val fakePacket = endpoint.getValue("noise").jsonObject.getValue("fake_packet").jsonObject
        assertEquals("2-5", fakePacket.getValue("count").jsonPrimitive.content)
        assertEquals("20-40", fakePacket.getValue("size").jsonPrimitive.content)
        assertEquals("1-9", fakePacket.getValue("delay").jsonPrimitive.content)
    }

    @Test
    fun `invalid enabled WARP registration fails instead of silently changing route`() {
        val invalid = warpConfig(WarpMode.DIRECT).copy(
            registration = registration().copy(privateKey = "invalid"),
        )
        val error = assertFailsWith<IllegalArgumentException> {
            NinetyConfigBuilder.build(
                listOf(configNode()),
                options = SingBoxOptions(warp = invalid),
            )
        }
        assertTrue(error.message.orEmpty().contains("WARP включён"))
    }

    @Test
    fun `custom routing and WARP decoration remain byte deterministic`() {
        val options = SingBoxOptions(
            warp = warpConfig(WarpMode.DIRECT),
            customRules = listOf(
                RoutingRule("one", type = RoutingRuleType.DOMAIN, values = listOf("example.com")),
            ),
        )
        val first = NinetyConfigBuilder.build(listOf(configNode()), options = options)
        val second = NinetyConfigBuilder.build(listOf(configNode()), options = options)
        assertEquals(first, second)
    }

    private fun warpConfig(mode: WarpMode) = WarpConfig(
        settings = WarpSettings(enabled = true, mode = mode),
        registration = registration(),
    )

    private fun registration() = WarpRegistration(
        registrationId = "registration-id",
        accountId = "account-id",
        accessToken = "token",
        privateKey = Base64.getEncoder().encodeToString(ByteArray(32) { 1 }),
        peerPublicKey = Base64.getEncoder().encodeToString(ByteArray(32) { 2 }),
        localIpv4 = "172.16.0.2",
        localIpv6 = "2606:4700:110:8765::2",
        clientId = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, -1)),
        registeredAt = "2026-07-20T00:00:00Z",
    )

    private fun configNode(): ConfigNode {
        val fixture = ProxyFixtures.all.first { it.id == "vless" }
        return ConfigNode("vless", requireNotNull(ProxyLinkParser.parseLink(fixture.link)))
    }
}
