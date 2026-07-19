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
import androidx.compose.foundation.lazy.grid.item
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
import pw.x4.ninety.ui.components.ScreenHeader
import pw.x4.ninety.ui.components.topHairline
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
import pw.x4.ninety.vpn.VpnController

@Composable
fun NodesScreen(metrics: NinetyLayoutMetrics) {
    val context = LocalContext.current
    val profile = Store.activeProfile()
    val rawNodes = Store.activeProfileNodes()
    val snapshot = ClashMonitor.snapshot
    val connected = VpnController.state == ConnState.Connected
    val nodes = remember(rawNodes, snapshot.delays) { sortByPing(rawNodes, snapshot.delays) }

    NinetyPage(metrics) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(top = 22.dp)) {
                ScreenHeader(
                    kicker = "Nodes" + (profile?.name?.let { " · $it" } ?: ""),
                    title = "Ноды",
                    sub = when {
                        nodes.isEmpty() -> "Профиль не выбран или не содержит нод"
                        connected -> "${nodes.size} ${plural(nodes.size)} · выбор применяется к активному туннелю"
                        else -> "${nodes.size} ${plural(nodes.size)} · пинг появится после подключения"
                    },
                )
                Spacer(Modifier.height(18.dp))

                if (nodes.isEmpty()) {
                    EmptyNodes(Modifier.fillMaxSize())
                } else {
                    LazyVerticalGrid(
                        columns = if (metrics.isCompact) GridCells.Fixed(1) else GridCells.Adaptive(286.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = if (connected) 92.dp else 28.dp),
                    ) {
                        if (nodes.size >= 2) {
                            item(key = "__auto__", span = { GridItemSpan(maxLineSpan) }) {
                                val effective = nodeByTag(nodes, snapshot.autoNow)
                                AutoCard(
                                    selected = Store.isAutoActive,
                                    effectiveName = effective?.name?.ifBlank { effective.host },
                                    ping = snapshot.autoNow?.let { snapshot.delays[it] },
                                    onClick = { select(context, Store.AUTO_ID) },
                                )
                            }
                        }
                        items(nodes, key = { it.id }) { node ->
                            NodeCard(
                                node = node,
                                selected = node.id == Store.activeId,
                                ping = snapshot.delays[ConfigBuilder.tagOf(node)],
                                onClick = { select(context, node.id) },
                            )
                        }
                    }
                }
            }

            if (connected && nodes.isNotEmpty()) {
                TestAllFab(
                    testing = snapshot.testing,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 22.dp),
                ) { ClashMonitor.urlTestAll() }
            }
        }
    }
}

@Composable
private fun EmptyNodes(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(top = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Ink.Ink2)
                .border(1.dp, Ink.Line2, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(NinetyIcons.Nodes, null, tint = Ink.TextFaint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("NODES · EMPTY", style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(8.dp))
        Text("Нет доступных нод", style = NinetyTypography.titleLarge, color = Ink.TextHi)
        Spacer(Modifier.height(8.dp))
        Text(
            "Добавьте профиль или выберите подписку с поддерживаемыми конфигурациями.",
            style = NinetyTypography.bodyMedium,
            color = Ink.TextMid,
        )
    }
}

@Composable
private fun AutoCard(
    selected: Boolean,
    effectiveName: String?,
    ping: Int?,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) Brush.horizontalGradient(0f to pack.accentSoft, 0.45f to Ink.Ink1, 1f to Ink.Ink1)
                else SolidColor(Ink.Ink1),
                shape,
            )
            .border(1.dp, if (selected) pack.accentSoft else Ink.Line2, shape)
            .topHairline(
                color = if (selected) pack.accent else Color.White,
                alpha = if (selected) 0.5f else if (pack.palette.isLight) 0.42f else 0.08f,
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(pack.accentSoft)
                .border(1.dp, pack.accentSoft, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(NinetyIcons.Refresh, null, tint = pack.accentBright, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Авто", color = Ink.TextHi, style = NinetyTypography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text("URLTEST", style = KickerStyle, color = pack.accent)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                effectiveName?.let { "Сейчас → $it" } ?: "Быстрейший узел по задержке",
                color = if (effectiveName != null) pack.accentBright else Ink.TextLo,
                style = MonoStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        PingPill(ping)
    }
}

@Composable
private fun NodeCard(node: Node, selected: Boolean, ping: Int?, onClick: () -> Unit) {
    val pack = NinetyState.pack
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) Brush.verticalGradient(0f to pack.accentSoft, 0.35f to Ink.Ink1, 1f to Ink.Ink1)
                else SolidColor(Ink.Ink1),
                shape,
            )
            .border(1.dp, if (selected) pack.accentSoft else Ink.Line2, shape)
            .topHairline(
                color = if (selected) pack.accent else Color.White,
                alpha = if (selected) 0.5f else if (pack.palette.isLight) 0.42f else 0.08f,
            )
            .clickable { onClick() }
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (selected) pack.accent else Ink.Line3),
            )
            Spacer(Modifier.width(11.dp))
            Text(
                node.name.ifBlank { node.host },
                color = Ink.TextHi,
                style = NinetyTypography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            PingPill(ping)
        }
        Spacer(Modifier.height(11.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            NodeMeta("ПРОТОКОЛ", node.proto.uppercase())
            NodeMeta("ЗАЩИТА", node.security.uppercase().takeIf { it != "NONE" } ?: "PLAIN")
            NodeMeta("ТРАНСПОРТ", node.type.uppercase())
        }
        Spacer(Modifier.height(11.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line1))
        Spacer(Modifier.height(9.dp))
        Text(
            "${node.host}:${node.port}",
            color = Ink.TextLo,
            style = MonoStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NodeMeta(label: String, value: String) {
    Column {
        Text(label, style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MonoStyle, color = Ink.TextMid, maxLines = 1)
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
        modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(pack.accent)
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
    if (VpnController.isActive) NinetyVpnService.reload(context)
}

private fun nodeByTag(nodes: List<Node>, tag: String?): Node? =
    tag?.let { value -> nodes.firstOrNull { ConfigBuilder.tagOf(it) == value } }

private fun pingGrade(ms: Int?): Int = when {
    ms == null || ms <= 0 || ms >= 65000 -> 3
    ms < 800 -> 0
    ms < 1500 -> 1
    else -> 2
}

private fun sortByPing(nodes: List<Node>, delays: Map<String, Int>): List<Node> =
    nodes.withIndex().sortedWith(
        compareBy(
            { pingGrade(delays[ConfigBuilder.tagOf(it.value)]) },
            { delays[ConfigBuilder.tagOf(it.value)]?.takeIf { delay -> delay > 0 } ?: 99999 },
            { it.index },
        ),
    ).map { it.value }

private fun plural(value: Int): String {
    val mod10 = value % 10
    val mod100 = value % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "нода"
        mod10 in 2..4 && mod100 !in 12..14 -> "ноды"
        else -> "нод"
    }
}
