package pw.x4.ninety.vpn

import android.os.Build
import pw.x4.ninety.core.config.ConfigNode
import pw.x4.ninety.core.config.FragmentMode
import pw.x4.ninety.core.config.Ipv6Mode
import pw.x4.ninety.core.config.NinetyConfigBuilder
import pw.x4.ninety.core.config.RoutingPlatform
import pw.x4.ninety.core.config.SingBoxOptions
import pw.x4.ninety.core.config.TunStack
import pw.x4.ninety.core.model.ProxyNode
import pw.x4.ninety.core.model.ProxyProtocol
import pw.x4.ninety.core.model.RoutingRuleType
import pw.x4.ninety.data.Node
import pw.x4.ninety.data.Options

/** Android compatibility facade around the deterministic core config builder. */
object ConfigBuilder {
    fun build(
        nodes: List<Node>,
        selectedId: String?,
        logPath: String? = null,
        opts: Options.Data = Options.data,
    ): String {
        val configNodes = nodes.map { it.toConfigNode() }
        val mode = TunnelModes.current(opts)
        val selection = if (mode.requiresProxySelection) {
            QualityRuntime.selectionForConfig(
                persistedSelection = selectedId,
                candidateNodeIds = configNodes.map(ConfigNode::id),
                options = opts,
            )
        } else {
            null
        }
        return NinetyConfigBuilder.build(
            nodes = configNodes,
            selection = selection,
            logPath = logPath,
            options = opts.toCoreOptions(),
        )
    }

    fun tagOf(node: Node): String = tagOfId(node.id)

    fun tagOfId(id: String): String = NinetyConfigBuilder.tagOfId(id)

    private fun Node.toConfigNode(): ConfigNode = ConfigNode(
        id = id,
        proxy = ProxyNode(
            protocol = ProxyProtocol.entries.firstOrNull { it.wireName == proto } ?: ProxyProtocol.VLESS,
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
            fingerprint = fp,
            publicKey = pbk,
            shortId = sid,
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
            certificatePublicKeySha256 = pinSHA256,
            congestionControl = congestion,
            udpRelayMode = udpRelay,
            insecure = insecure,
            zeroRttHandshake = zeroRtt,
            disableSni = disableSni,
            plugin = plugin,
            pluginOptions = pluginOpts,
            raw = raw,
        ),
    )

    private fun Options.Data.toCoreOptions(): SingBoxOptions = SingBoxOptions(
        testUrl = testUrl,
        testIntervalSec = testIntervalSec,
        logLevel = logLevel,
        logDisabled = logDisabled,
        region = region,
        blockAds = blockAds,
        bypassLan = bypassLan,
        ipv6Mode = Ipv6Mode.fromWire(ipv6Mode),
        customRules = customRules.filter { rule ->
            rule.type != RoutingRuleType.ANDROID_PACKAGE || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        },
        routingPlatform = RoutingPlatform.ANDROID,
        warp = WarpRuntime.configForBuild(this),
        dnsRemote = dnsRemote,
        dnsDirect = dnsDirect,
        fakeDns = fakeDns,
        independentCache = independentCache,
        mtu = mtu,
        tunStack = TunStack.fromWire(tunStack),
        strictRoute = strictRoute,
        tlsFragment = tlsFragment,
        fragmentMode = FragmentMode.fromWire(fragmentMode),
        mixedSniCase = mixedSniCase,
        tlsPadding = tlsPadding,
        paddingFrom = paddingFrom,
        paddingTo = paddingTo,
        muxEnable = muxEnable,
        muxProtocol = muxProtocol,
        muxMaxStreams = muxMaxStreams,
        muxPadding = muxPadding,
    )
}
