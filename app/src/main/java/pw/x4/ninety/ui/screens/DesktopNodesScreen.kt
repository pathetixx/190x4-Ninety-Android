package pw.x4.ninety.ui.screens

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pw.x4.ninety.data.Node
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.components.desktopCard
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.layout.NinetyLayoutMetrics
import pw.x4.ninety.ui.layout.NinetyPage
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.vpn.ClashMonitor
import pw.x4.ninety.vpn.ConfigBuilder
import pw.x4.ninety.vpn.ConnState
import pw.x4.ninety.vpn.NinetyVpnService
import pw.x4.ninety.vpn.ProbePhase
import pw.x4.ninety.vpn.TunnelMode
import pw.x4.ninety.vpn.TunnelModes
import pw.x4.ninety.vpn.VpnController

@Composable
fun DesktopNodesScreen(metrics: NinetyLayoutMetrics) {
    val context = LocalContext.current
    val profile = Store.activeProfile()
    val rawNodes = Store.activeProfileNodes()
    val monitor = ClashMonitor.snapshot
    val connected = VpnController.state == ConnState.Connected
    val nodes = remember(rawNodes, monitor.delays) { sortDesktopNodes(rawNodes, monitor.delays) }

    NinetyPage(metrics) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(top = if (metrics.isCompact) 16.dp else 24.dp)) {
                Column {
                    Text("PROXY FLEET · ${nodes.size}", style = KickerStyle, color = Ink.TextFaint)
                    Spacer(Modifier.height(5.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("Ноды", style = NinetyTypography.headlineMedium, color = Ink.TextHi)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                profile?.let { "$it · native Auto sing-box · ${probePhaseLabel(monitor.phase)}" }
                                    ?: "Профиль не выбран",
                                style = NinetyTypography.bodyMedium,
                                color = Ink.TextMid,
                            )
                        }
                        Text(
                            TunnelModes.current().title.uppercase(),
                            style = KickerStyle,
                            color = NinetyState.pack.material.secondary,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                DesktopProbeBanner(monitor.phase, monitor.lastError, monitor.measuredAtMs, nodes.size)
                Spacer(Modifier.height(12.dp))

                if (nodes.isEmpty()) {
                    DesktopEmptyNodes(Modifier.fillMaxSize())
                } else {
                    LazyVerticalGrid(
                        columns = if (metrics.isCompact || metrics.isExpanded) GridCells.Fixed(1) else GridCells.Adaptive(310.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(if (metrics.isExpanded) 7.dp else 10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            bottom = if (connected && TunnelModes.current() != TunnelMode.WARP_DIRECT) 92.dp else 28.dp,
                        ),
                    ) {
                        if (nodes.size >= 2) {
                            item(key = "__auto__", span = { GridItemSpan(maxLineSpan) }) {
                                val effective = desktopNodeByTag(nodes, monitor.autoNow)
                                DesktopFleetRow(
                                    auto = true,
                                    node = null,
                                    selected = Store.isAutoActive,
                                    effective = Store.isAutoActive && effective != null,
                                    effectiveName = effective?.let { it.name.ifBlank { it.host } },
                                    ping = monitor.autoNow?.let(monitor.delays::get),
                                    phase = monitor.phase,
                                    compact = !metrics.isExpanded,
                                    onClick = { selectDesktopNode(context, Store.AUTO_ID) },
                                )
                            }
                        }
                        items(nodes, key = { it.id }) { node ->
                            DesktopFleetRow(
                                auto = false,
                                node = node,
                                selected = node.id == Store.activeId,
                                effective = ConfigBuilder.tagOf(node) == monitor.effectiveTag(),
                                effectiveName = null,
                                ping = monitor.delays[ConfigBuilder.tagOf(node)],
                                phase = monitor.phase,
                                compact = !metrics.isExpanded,
                                onClick = { selectDesktopNode(context, node.id) },
                            )
                        }
                    }
                }
            }

            if (connected && nodes.isNotEmpty() && TunnelModes.current() != TunnelMode.WARP_DIRECT) {
                DesktopTestAllFab(
                    testing = monitor.testing,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 22.dp),
                ) { ClashMonitor.urlTestAll() }
            }
        }
    }
}

@Composable
private fun DesktopProbeBanner(phase: ProbePhase, error: String?, measuredAtMs: Long, nodeCount: Int) {
    val pack = NinetyState.pack
    val (label, color) = when (phase) {
        ProbePhase.Offline -> "PING · OFFLINE" to Ink.TextFaint
        ProbePhase.Connecting -> "PING · CONNECTING" to Ink.Warn
        ProbePhase.Testing -> "PING · TESTING" to pack.accentBright
        ProbePhase.Partial -> "PING · PARTIAL" to Ink.Warn
        ProbePhase.Ready -> "PING · READY" to Ink.Ok
        ProbePhase.Error -> "PING · ERROR" to Ink.Err
    }
    val measured = ClashMonitor.snapshot.delays.values.count(::desktopValidPing)
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(NinetyRadius.sm))
            .desktopCard().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.size(9.dp))
        Text(label, style = KickerStyle, color = color)
        Spacer(Modifier.size(14.dp))
        Text(
            error ?: when {
                measuredAtMs > 0 -> "$measured / $nodeCount результатов"
                phase == ProbePhase.Offline -> "Запустите VPN для проверки"
                else -> "Ожидание CommandClient"
            },
            style = MonoStyle,
            color = Ink.TextLo,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("10s timeout", style = KickerStyle, color = Ink.TextFaint)
    }
}

@Composable
private fun DesktopEmptyNodes(modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(NinetyRadius.lg)).desktopCard(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(NinetyIcons.Nodes, null, tint = Ink.TextFaint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("NODES · EMPTY", style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(8.dp))
        Text("Нет доступных нод", style = NinetyTypography.titleLarge, color = Ink.TextHi)
        Spacer(Modifier.height(8.dp))
        Text("Добавьте профиль или используйте WARP Direct.", style = NinetyTypography.bodyMedium, color = Ink.TextMid)
    }
}

@Composable
private fun DesktopTestAllFab(testing: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pack = NinetyState.pack
    val spin = rememberInfiniteTransition(label = "desktopNodesFab")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Box(
        modifier.size(54.dp).clip(CircleShape).background(pack.accent)
            .border(1.dp, pack.material.secondary, CircleShape)
            .clickable(enabled = !testing) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(NinetyIcons.Refresh, "Проверить все", tint = Ink.Ink0, modifier = Modifier.size(23.dp).rotate(if (testing) angle else 0f))
    }
}

private fun selectDesktopNode(context: Context, id: String) {
    Store.setActive(id)
    if (VpnController.isActive && TunnelModes.current().requiresProxySelection) NinetyVpnService.reload(context)
}

private fun desktopNodeByTag(nodes: List<Node>, tag: String?): Node? = tag?.let { value -> nodes.firstOrNull { ConfigBuilder.tagOf(it) == value } }

private fun sortDesktopNodes(nodes: List<Node>, delays: Map<String, Int>): List<Node> = nodes.withIndex().sortedWith(
    compareBy(
        { desktopPingGrade(delays[ConfigBuilder.tagOf(it.value)]) },
        { delays[ConfigBuilder.tagOf(it.value)]?.takeIf(::desktopValidPing) ?: Int.MAX_VALUE },
        { it.index },
    ),
).map { it.value }

private fun desktopPingGrade(ms: Int?): Int = when {
    !desktopValidPing(ms) -> 3
    ms!! < 800 -> 0
    ms < 1500 -> 1
    else -> 2
}

private fun desktopValidPing(ms: Int?): Boolean = ms != null && ms in 1 until 65_000
