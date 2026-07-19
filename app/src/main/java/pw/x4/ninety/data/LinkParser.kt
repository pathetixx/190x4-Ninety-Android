package pw.x4.ninety.data

import pw.x4.ninety.core.model.ProxyNode
import pw.x4.ninety.core.parser.ProxyLinkParser

/**
 * Android compatibility facade over the pure Kotlin `:core:parser` module.
 *
 * Store/UI keep working with the legacy [Node] model while parsing itself no longer
 * depends on android.net.Uri, android.util.Base64 or org.json.
 */
object LinkParser {
    fun parseLink(raw: String): Node? = ProxyLinkParser.parseLink(raw)?.toNode()

    fun parseSubscription(content: String): List<Node> =
        ProxyLinkParser.parseSubscription(content).map { it.toNode() }
}

private fun ProxyNode.toNode(): Node = Node(
    proto = protocol.wireName,
    name = name,
    host = host,
    port = port,
    uuid = uuid,
    password = password,
    method = method,
    cipher = cipher,
    alterId = alterId,
    security = security,
    type = type,
    flow = flow,
    sni = sni,
    fp = fingerprint,
    pbk = publicKey,
    sid = shortId,
    alpn = alpn,
    path = path,
    hostHeader = hostHeader,
    serviceName = serviceName,
    mode = mode,
    extra = extra,
    upMbps = upMbps,
    downMbps = downMbps,
    obfs = obfs,
    obfsPassword = obfsPassword,
    congestion = congestionControl,
    udpRelay = udpRelayMode,
    insecure = insecure,
    zeroRtt = zeroRttHandshake,
    raw = raw,
)
