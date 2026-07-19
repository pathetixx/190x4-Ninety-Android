package pw.x4.ninety.core.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxyLinkParserTest {
    @Test
    fun `desktop fixtures normalize stable fields`() {
        fixtures().forEach { fixture ->
            val parsed = assertNotNull(ProxyLinkParser.parseLink(fixture.link), fixture.protocol)

            assertEquals(fixture.protocol, parsed.protocol.wireName)
            assertEquals(fixture.name, parsed.name)
            assertEquals(fixture.host, parsed.host)
            assertEquals(fixture.port, parsed.port)
            assertEquals(fixture.type, parsed.type)
            assertEquals(fixture.security, parsed.security)
        }
    }

    @Test
    fun `protocol specific fields survive normalization`() {
        val parsed = fixtures()
            .map { assertNotNull(ProxyLinkParser.parseLink(it.link)) }
            .associateBy(ParsedProxy::protocol)

        parsed.getValue(ProxyProtocol.VLESS).let { node ->
            assertEquals("xtls-rprx-vision", node.flow)
            assertEquals("public-key", node.publicKey)
            assertEquals("abcd", node.shortId)
            assertEquals("/api", node.path)
            assertEquals("cdn.example.com", node.hostHeader)
            assertEquals("{\"xPaddingBytes\":\"100-200\"}", node.extra)
        }

        parsed.getValue(ProxyProtocol.VMESS).let { node ->
            assertEquals("auto", node.cipher)
            assertEquals("cdn.example.com", node.hostHeader)
            assertEquals("/socket", node.path)
            assertEquals("edge.example.com", node.sni)
        }

        parsed.getValue(ProxyProtocol.TROJAN).let { node ->
            assertEquals("p@ss:word", node.password)
            assertEquals("trojan-grpc", node.serviceName)
        }

        parsed.getValue(ProxyProtocol.SHADOWSOCKS).let { node ->
            assertEquals("aes-256-gcm", node.method)
            assertEquals("p@ss:w0rd", node.password)
            assertEquals("v2ray-plugin", node.plugin)
            assertEquals("mode=websocket", node.pluginOptions)
        }

        parsed.getValue(ProxyProtocol.HYSTERIA2).let { node ->
            assertEquals("hy@pass", node.password)
            assertEquals("salamander", node.obfs)
            assertEquals("secret", node.obfsPassword)
            assertEquals(50, node.upMbps)
            assertEquals(200, node.downMbps)
            assertTrue(node.insecure)
        }

        parsed.getValue(ProxyProtocol.TUIC).let { node ->
            assertEquals("tuic:pass", node.password)
            assertEquals("bbr", node.congestionControl)
            assertEquals("native", node.udpRelayMode)
            assertTrue(node.zeroRttHandshake)
        }
    }

    @Test
    fun `plain and base64 subscriptions produce identical nodes`() {
        val plain = resource("subscription-plain.txt")
        val encoded = resource("subscription-base64.txt")

        val plainNodes = ProxyLinkParser.parseSubscription(plain)
        val encodedNodes = ProxyLinkParser.parseSubscription(encoded)

        assertEquals(6, plainNodes.size)
        assertEquals(plainNodes, encodedNodes)
        assertEquals(ProxyProtocol.entries.toSet(), plainNodes.map(ParsedProxy::protocol).toSet())
    }

    @Test
    fun `bad rows are skipped and do not poison a subscription`() {
        val valid = fixtures().first().link
        val content = """
            definitely-not-a-link
            vmess://not-base64
            vless://missing-endpoint
            $valid
        """.trimIndent()

        val parsed = ProxyLinkParser.parseSubscription(content)

        assertEquals(1, parsed.size)
        assertEquals(ProxyProtocol.VLESS, parsed.single().protocol)
        assertNull(ProxyLinkParser.parseLink("https://example.com/subscription"))
    }

    @Test
    fun `quic aliases and advanced tls fields stay Android compatible`() {
        val hy2 = assertNotNull(
            ProxyLinkParser.parseLink(
                "hy2://secret@hy.example.com?insecure=true&pinSHA256=sha256-pin#Alias"
            )
        )
        val tuic = assertNotNull(
            ProxyLinkParser.parseLink(
                "tuic://uuid:pass@tuic.example.com?allow_insecure=1&zero_rtt_handshake=1&disable_sni=true"
            )
        )

        assertEquals(443, hy2.port)
        assertTrue(hy2.insecure)
        assertEquals("sha256-pin", hy2.certificatePublicKeySha256)
        assertEquals(443, tuic.port)
        assertTrue(tuic.insecure)
        assertTrue(tuic.zeroRttHandshake)
        assertTrue(tuic.disableSni)
        assertFalse(ProxyLinkParser.parseSubscription("not base64 and not a link").isNotEmpty())
    }

    private fun fixtures(): List<Fixture> = resource("desktop-compatible-links.tsv")
        .lineSequence()
        .filter { it.isNotBlank() && !it.startsWith('#') }
        .map { row ->
            val columns = row.split('\t', limit = 7)
            require(columns.size == 7) { "Bad fixture row: $row" }
            Fixture(
                protocol = columns[0],
                name = columns[1],
                host = columns[2],
                port = columns[3].toInt(),
                type = columns[4],
                security = columns[5],
                link = columns[6],
            )
        }
        .toList()

    private fun resource(name: String): String =
        requireNotNull(javaClass.getResource("/$name")) { "Missing test resource: $name" }.readText()

    private data class Fixture(
        val protocol: String,
        val name: String,
        val host: String,
        val port: Int,
        val type: String,
        val security: String,
        val link: String,
    )
}
