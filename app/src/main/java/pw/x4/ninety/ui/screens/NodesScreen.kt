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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pw.x4.ninety.data.Node
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.components.PingPill
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.layout.NinetyLayoutMetrics
import pw.x4.ninety.ui.layout.NinetyPage
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
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
fun NodesScreen(metrics: NinetyLayoutMetrics) {
    val context = LocalContext.current
    val profile = Store.activeProfile()
    val rawNodes = Store.activeProfileNodes()
    val monitor = ClashMonitor.snapshot
    val connected = VpnController.state == ConnState.Connected
    val nodes = remember(rawNodes, monitor.delays) { sortByPing(rawNodes, monitor.delays) }

    NinetyPage(metrics) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(top = if (metrics.isCompact) 16.dp else 24.dp)) {
                NodesHeader(profile?.name, nodes.size, monitor.phase)
                Spacer(Modifier.height(14.dp))
                ProbeBanner(monitor.phase, monitor.lastError, monitor.measuredAtMs, nodes.size)
                Spacer(Modifier.height(12.dp))

                if (nodes.isEmpty()) {
                    EmptyNodes(Modifier.fillMaxSize())
                } else {
                    LazyVerticalGrid(
                        columns = if (metrics.isExpanded || metrics.isCompact) GridCells.Fixed(1) else GridCells.Adaptive(310.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(if (metrics.isExpanded) 7.dp else 10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            bottom = if (connected && TunnelModes.current() != TunnelMode.WARP_DIRECT) 92.dp else 28.dp,
                        ),
                    ) {
                        if (nodes.size >= 2) {
                            item(key = "__auto__", span = { GridItemSpan(maxLineSpan) }) {
                                val effective = nodeByTag(nodes, monitor.autoNow)
                                if (metrics.isExpanded) {
                                    DesktopAutoRow(
                                        selected = Store.isAutoActive,
                                        effectiveName = effective?.let { it.name.ifBlank { it.host } },
                                        ping = monitor.autoNow?.let(monitor.delays::get),
                                        phase = monitor.phase,
                                        onClick = { select(context, Store.AUTO_ID) },
                                    )
                                } else {
                                    CompactAutoCard(
                                        selected = Store.isAutoActive,
                                        effectiveName = effective?.let { it.name.ifBlank { it.host } },
                                        ping = monitor.autoNow?.let(monitor.delays::get),
                                        phase = monitor.phase,
                                        onClick = { select(context, Store.AUTO_ID) },
                                    )
                                }
                            }
                        }
                        items(nodes, key = { it.id }) { node ->
                            val ping = monitor.delays[ConfigBuilder.tagOf(node)]
                            if (metrics.isExpanded) {
                                DesktopNodeRow(
                                    node = node,
                                    selected = node.id == Store.activeId,
                                    effective = ConfigBuilder.tagOf(node) == monitor.effectiveTag(),
                                    ping = ping,
                                    onClick = { select(context, node.id) },
                                )
                            } else {
                                CompactNodeCard(
                                    node = node,
                                    selected = node.id == Store.activeId,
                                    effective = ConfigBuilder.tagOf(node) == monitor.effectiveTag(),
                                    ping = ping,
                                    onClick = { select(context, node.id) },
                                )
                            }
                        }
                    }
                }
            }

            if (connected && nodes.isNotEmpty() && TunnelModes.current() != TunnelMode.WARP_DIRECT) {
                TestAllFab(
                    testing = monitor.testing,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 22.dp),
                ) { ClashMonitor.urlTestAll() }
            }
        }
    }
}

@Composable
private fun NodesHeader(profile: String?, count: Int, phase: ProbePhase) {
    Column {
        Text("PROXY FLEET · $count", style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Ноды", style = NinetyTypography.headlineMedium, color = Ink.TextHi)
                Spacer(Modifier.height(5.dp))
                Text(
                    profile?.let { "$it · штатный Auto sing-box · ${phaseLabel(phase)}" }
                        ?: "Профиль не выбран",
                    style = NinetyTypography.bodyMedium,
                    color = Ink.TextMid,
                )
            }
            Text(
                TunnelModes.current().title.uppercase(),
                style = KickerStyle,
                color = NinetyState.pack.accentBright,
            )
        }
    }
}

@Composable
private fun ProbeBanner(
    phase: ProbePhase,
    error: String?,
    measuredAtMs: Long,
    nodeCount: Int,
) {
    val pack = NinetyState.pack
    val (label, color) = when (phase) {
        ProbePhase.Offline -> "PING · OFFLINE" to Ink.TextFaint
        ProbePhase.Connecting -> "PING · CONNECTING" to Ink.Warn
        ProbePhase.Testing -> "PING · TESTING" to pack.accentBright
        ProbePhase.Partial -> "PING · PARTIAL" to Ink.Warn
        ProbePhase.Ready -> "PING · READY" to Ink.Ok
        ProbePhase.Error -> "PING · ERROR" to Ink.Err
    }
    val measured = ClashMonitor.snapshot.delays.values.count(::validPing)
    Row(
        Modifier.fillMaxWidth().height(46.dp).background(Ink.Ink1)
            .border(1.dp, Ink.Line1).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(9.dp))
        Text(label, style = KickerStyle, color = color)
        Spacer(Modifier.width(14.dp))
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
private fun DesktopAutoRow(
    selected: Boolean,
    effectiveName: String?,
    ping: Int?,
    phase: ProbePhase,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    Row(
        Modifier.fillMaxWidth().height(72.dp)
            .background(if (selected) Brush.horizontalGradient(listOf(pack.accentSoft, Ink.Ink1, Ink.Ink1)) else SolidColor(Ink.Ink1))
            .border(1.dp, if (selected) pack.accent else Ink.Line1)
            .clickable { onClick() }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(if (selected) Ink.Ok else Ink.TextFaint, CircleShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1.4f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Авто", style = NinetyTypography.titleMedium, color = Ink.TextHi)
                Spacer(Modifier.width(9.dp))
                FleetBadge("NATIVE URLTEST", selected)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                effectiveName?.let { "Сейчас → $it" }
                    ?: if (phase == ProbePhase.Testing) "Выполняется замер" else "Ожидание результата auto-group",
                style = MonoStyle,
                color = if (effectiveName != null) pack.accentBright else Ink.TextLo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DesktopMeta("POLICY", "URLTEST", Modifier.width(92.dp))
        DesktopMeta("STATE", phaseLabel(phase).uppercase(), Modifier.width(104.dp))
        Box(Modifier.width(86.dp), contentAlignment = Alignment.CenterEnd) { PingPill(ping) }
    }
}

@Composable
private fun DesktopNodeRow(
    node: Node,
    selected: Boolean,
    effective: Boolean,
    ping: Int?,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    Row(
        Modifier.fillMaxWidth().height(70.dp)
            .background(if (selected) Brush.horizontalGradient(listOf(pack.accentSoft, Ink.Ink1, Ink.Ink1)) else SolidColor(Ink.Ink1))
            .border(1.dp, if (selected || effective) pack.accentSoft else Ink.Line1)
            .clickable { onClick() }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(if (effective) Ink.Ok else if (selected) pack.accent else Ink.TextFaint, CircleShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1.45f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(node.name.ifBlank { node.host }, style = NinetyTypography.titleMedium, color = Ink.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (effective) {
                    Spacer(Modifier.width(8.dp))
                    FleetBadge("IN USE", true)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("${node.host}:${node.port}", style = MonoStyle, color = Ink.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DesktopMeta("PROTOCOL", node.proto.uppercase(), Modifier.width(100.dp))
        DesktopMeta("SECURITY", node.security.uppercase().takeIf { it != "NONE" } ?: "PLAIN", Modifier.width(104.dp))
        DesktopMeta("TRANSPORT", node.type.uppercase(), Modifier.width(100.dp))
        Box(Modifier.width(86.dp), contentAlignment = Alignment.CenterEnd) { PingPill(ping) }
    }
}

@Composable
private fun DesktopMeta(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MonoStyle, color = Ink.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CompactAutoCard(
    selected: Boolean,
    effectiveName: String?,
    ping: Int?,
    phase: ProbePhase,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) Brush.horizontalGradient(listOf(pack.accentSoft, Ink.Ink1, Ink.Ink1)) else SolidColor(Ink.Ink1))
            .border(1.dp, if (selected) pack.accentSoft else Ink.Line1, shape)
            .clickable { onClick() }.padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(pack.accentSoft), contentAlignment = Alignment.Center) {
            Icon(NinetyIcons.Refresh, null, tint = pack.accentBright, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Авто · NATIVE URLTEST", color = Ink.TextHi, style = NinetyTypography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                effectiveName?.let { "Сейчас → $it" } ?: phaseLabel(phase),
                color = if (effectiveName != null) pack.accentBright else Ink.TextLo,
                style = MonoStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(9.dp))
        PingPill(ping)
    }
}

@Composable
private fun CompactNodeCard(node: Node, selected: Boolean, effective: Boolean, ping: Int?, onClick: () -> Unit) {
    val pack = NinetyState.pack
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) Brush.verticalGradient(listOf(pack.accentSoft, Ink.Ink1, Ink.Ink1)) else SolidColor(Ink.Ink1))
            .border(1.dp, if (selected || effective) pack.accentSoft else Ink.Line1, shape)
            .clickable { onClick() }.padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (effective) Ink.Ok else if (selected) pack.accent else Ink.TextFaint, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(node.name.ifBlank { node.host }, color = Ink.TextHi, style = NinetyTypography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            PingPill(ping)
        }
        Spacer(Modifier.height(10.dp))
        Text("${node.host}:${node.port}", color = Ink.TextLo, style = MonoStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CompactMeta("PROTOCOL", node.proto.uppercase())
            CompactMeta("SECURITY", node.security.uppercase().takeIf { it != "NONE" } ?: "PLAIN")
            CompactMeta("TRANSPORT", node.type.uppercase())
        }
    }
}

@Composable
private fun CompactMeta(label: String, value: String) {
    Column {
        Text(label, style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MonoStyle, color = Ink.TextMid, maxLines = 1)
    }
}

@Composable
private fun FleetBadge(text: String, active: Boolean) {
    Text(
        text,
        style = KickerStyle,
        color = if (active) NinetyState.pack.accentBright else Ink.TextMid,
        modifier = Modifier.background(if (active) NinetyState.pack.accentSoft else Ink.Ink3, RoundedCornerShape(4.dp))
            .border(1.dp, if (active) NinetyState.pack.accent else Ink.Line2, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun EmptyNodes(modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(Ink.Ink2).border(1.dp, Ink.Line2), contentAlignment = Alignment.Center) {
            Icon(NinetyIcons.Nodes, null, tint = Ink.TextFaint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("NODES · EMPTY", style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(8.dp))
        Text("Нет доступных нод", style = NinetyTypography.titleLarge, color = Ink.TextHi)
        Spacer(Modifier.height(8.dp))
        Text("Добавьте профиль или используйте зарегистрированный WARP Direct.", style = NinetyTypography.bodyMedium, color = Ink.TextMid)
    }
}

@Composable
private fun TestAllFab(testing: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pack = NinetyState.pack
    val spin = rememberInfiniteTransition(label = "nodesFabSpin")
    val angle by spin.animateFloat(
        0f,
        360f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "nodesFabAngle",
    )
    Box(
        modifier.size(54.dp).clip(CircleShape).background(pack.accent)
            .border(1.dp, pack.accentBright, CircleShape)
            .clickable(enabled = !testing) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            NinetyIcons.Refresh,
            contentDescription = "Проверить задержку всех нод",
            tint = Ink.Ink0,
            modifier = Modifier.size(23.dp).rotate(if (testing) angle else 0f),
        )
    }
}

private fun select(context: Context, id: String) {
    Store.setActive(id)
    if (VpnController.isActive && TunnelModes.current().requiresProxySelection) NinetyVpnService.reload(context)
}

private fun nodeByTag(nodes: List<Node>, tag: String?): Node? =
    tag?.let { value -> nodes.firstOrNull { ConfigBuilder.tagOf(it) == value } }

private fun sortByPing(nodes: List<Node>, delays: Map<String, Int>): List<Node> =
    nodes.withIndex().sortedWith(
        compareBy(
            { pingGrade(delays[ConfigBuilder.tagOf(it.value)]) },
            { delays[ConfigBuilder.tagOf(it.value)]?.takeIf(::validPing) ?: Int.MAX_VALUE },
            { it.index },
        ),
    ).map { it.value }

private fun pingGrade(ms: Int?): Int = when {
    !validPing(ms) -> 3
    ms!! < 800 -> 0
    ms < 1500 -> 1
    else -> 2
}

private fun validPing(ms: Int?): Boolean = ms != null && ms in 1 until 65_000

private fun phaseLabel(phase: ProbePhase): String = when (phase) {
    ProbePhase.Offline -> "offline"
    ProbePhase.Connecting -> "connecting"
    ProbePhase.Testing -> "testing"
    ProbePhase.Partial -> "partial"
    ProbePhase.Ready -> "ready"
    ProbePhase.Error -> "error"
}
