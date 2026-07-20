package pw.x4.ninety.core.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import pw.x4.ninety.core.model.ProxySelection
import pw.x4.ninety.core.parser.ProxyFixtures
import pw.x4.ninety.core.parser.ProxyLinkParser

class SingBoxConfigBuilderTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun `default VLESS config matches golden`() {
        val nodes = listOf(configNode("vless"))
        val first = SingBoxConfigBuilder.build(nodes, ProxySelection.Node("vless"))
        val second = SingBoxConfigBuilder.build(nodes, ProxySelection.Node("vless"))

        assertEquals(first, second, "builder output must be byte-deterministic")
        assertEquals(golden("golden-default-vless.json"), json.parseToJsonElement(first))
    }

    @Test
    fun `feature-rich VLESS config matches golden`() {
        val options = SingBoxOptions(
            testUrl = "https://example.com/ping",
            testIntervalSec = 30,
            logLevel = "debug",
            region = "ru",
            blockAds = true,
            bypassLan = false,
            ipv6Mode = Ipv6Mode.PREFER,
            dnsRemote = "tls://[2001:db8::53]:853",
            dnsDirect = "udp://8.8.8.8:53",
            fakeDns = true,
            independentCache = true,
            mtu = 1500,
            tunStack = TunStack.SYSTEM,
            strictRoute = true,
            tlsFragment = true,
            fragmentMode = FragmentMode.TCP,
            fragmentFallbackDelay = "300ms",
            mixedSniCase = true,
            tlsPadding = true,
            paddingFrom = 120,
            paddingTo = 640,
            muxEnable = true,
            muxProtocol = "smux",
            muxMaxStreams = 16,
            muxPadding = true,
        )

        val actual = SingBoxConfigBuilder.build(
            nodes = listOf(configNode("vless")),
            selection = ProxySelection.Auto,
            logPath = "/tmp/ninety.log",
            options = options,
        )

        assertEquals(golden("golden-feature-vless.json"), json.parseToJsonElement(actual))
    }

    @Test
    fun `all parser fixtures keep protocol-specific outbound fields`() {
        val nodes = ProxyFixtures.all.map { fixture ->
            val parsed = assertNotNull(ProxyLinkParser.parseLink(fixture.link), fixture.id)
            val enriched = when (fixture.id) {
                "hysteria2" -> parsed.copy(certificatePublicKeySha256 = "sha256-pin")
                "tuic" -> parsed.copy(disableSni = true)
                else -> parsed
            }
            ConfigNode(fixture.id, enriched)
        }

        val root = json.parseToJsonElement(
            SingBoxConfigBuilder.build(nodes, ProxySelection.Auto, options = SingBoxOptions(muxEnable = true))
        ).jsonObject

        outbound(root, "nshadowsocks").let {
            assertEquals("v2ray-plugin", it.string("plugin"))
            assertEquals("mode=websocket", it.string("plugin_opts"))
        }
        outbound(root, "nhysteria2").let {
            assertEquals(50, it["up_mbps"]!!.jsonPrimitive.int)
            assertEquals("sha256-pin", it["tls"]!!.jsonObject.string("certificate_public_key_sha256"))
            assertFalse(it.containsKey("multiplex"), "QUIC outbounds must not receive stream multiplex")
        }
        outbound(root, "ntuic").let {
            assertTrue(it["tls"]!!.jsonObject["disable_sni"]!!.jsonPrimitive.boolean)
            assertFalse(it.containsKey("multiplex"), "QUIC outbounds must not receive stream multiplex")
        }
        outbound(root, "ntrojan").let {
            assertEquals("trojan-grpc", it["transport"]!!.jsonObject.string("service_name"))
            assertTrue(it.containsKey("multiplex"))
        }
    }

    @Test
    fun `xhttp download settings are translated and unknown fields are dropped`() {
        val base = configNode("vless")
        val node = base.copy(proxy = base.proxy.copy(
            extra = """{
                "unknown":"drop-me",
                "downloadSettings":{
                    "address":"download.example.com",
                    "port":8443,
                    "security":"reality",
                    "realitySettings":{
                        "serverName":"download-sni.example.com",
                        "fingerprint":"firefox",
                        "publicKey":"download-key",
                        "shortId":"ef01"
                    },
                    "xhttpSettings":{
                        "path":"/down",
                        "extra":{"xPaddingBytes":"200-400"}
                    }
                }
            }""".trimIndent(),
        ))

        val root = json.parseToJsonElement(SingBoxConfigBuilder.build(listOf(node))).jsonObject
        val transport = outbound(root, "nvless")["transport"]!!.jsonObject
        val download = transport["downloadSettings"]!!.jsonObject

        assertFalse(transport.containsKey("unknown"))
        assertEquals("download.example.com", download.string("server"))
        assertEquals(8443, download["server_port"]!!.jsonPrimitive.int)
        assertEquals("/down", download.string("path"))
        assertEquals("200-400", download.string("xPaddingBytes"))
        assertEquals("download-key", download["tls"]!!.jsonObject["reality"]!!.jsonObject.string("public_key"))
    }

    @Test
    fun `duplicate ids are deduplicated and invalid input fails early`() {
        val first = configNode("vless")
        val duplicate = configNode("vmess").copy(id = "vless")
        val root = json.parseToJsonElement(
            SingBoxConfigBuilder.build(listOf(first, duplicate), ProxySelection.Node("vless"))
        ).jsonObject
        val proxyOutbounds = root["outbounds"]!!.jsonArray
            .map { it.jsonObject }
            .filter { it.string("tag").startsWith('n') }

        assertEquals(listOf("nvless"), proxyOutbounds.map { it.string("tag") })
        assertFailsWith<IllegalArgumentException> { SingBoxConfigBuilder.build(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            SingBoxConfigBuilder.build(listOf(first), options = SingBoxOptions(dnsRemote = "ftp://dns.example.com"))
        }
    }

    private fun configNode(id: String): ConfigNode {
        val fixture = requireNotNull(ProxyFixtures.all.firstOrNull { it.id == id })
        return ConfigNode(id, assertNotNull(ProxyLinkParser.parseLink(fixture.link)))
    }

    private fun outbound(root: JsonObject, tag: String): JsonObject = root["outbounds"]!!
        .jsonArray
        .map { it.jsonObject }
        .first { it.string("tag") == tag }

    private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.content.orEmpty()

    private fun golden(name: String) = json.parseToJsonElement(
        requireNotNull(javaClass.getResource("/$name")) { "missing golden: $name" }.readText()
    )
}
