package pw.x4.ninety.ui.screens

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pw.x4.ninety.data.Node
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.components.PingPill
import pw.x4.ninety.ui.components.ScreenHeader
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.vpn.ClashMonitor
import pw.x4.ninety.vpn.ConfigBuilder
import pw.x4.ninety.vpn.ConnState
import pw.x4.ninety.vpn.NinetyVpnService
import pw.x4.ninety.vpn.VpnController

@Composable
fun NodesScreen() {
    val context = LocalContext.current
    val profile = Store.activeProfile()
    val nodes = Store.activeProfileNodes()
    val snap = ClashMonitor.snapshot
    val connected = VpnController.state == ConnState.Connected

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            ScreenHeader(
                kicker = "Nodes" + (profile?.name?.let { " · $it" } ?: ""),
                title = "Ноды",
                sub = when {
                    nodes.isEmpty() -> "Профиль не выбран"
                    connected -> "${nodes.size} ${plural(nodes.size)} · нажмите для выбора, ⚡ — перетест"
                    else -> "${nodes.size} ${plural(nodes.size)} · пинг появится после подключения"
                },
            )
            Spacer(Modifier.height(16.dp))

            if (nodes.isEmpty()) {
                Text(
                    "Нет нод. Добавьте профиль во вкладке «Профили» —\nего серверы появятся здесь.",
                    color = Ink.TextMid, style = NinetyTypography.bodyMedium,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 88.dp), // под FAB
                ) {
                    if (nodes.size >= 2) {
                        item(key = "__auto__") {
                            val effNode = nodeByTag(nodes, snap.autoNow)
                            AutoRow(
                                selected = Store.isAutoActive,
                                effectiveName = effNode?.let { it.name.ifBlank { it.host } },
                                ping = snap.autoNow?.let { snap.delays[it] },
                            ) { select(context, Store.AUTO_ID) }
                        }
                    }
                    items(nodes, key = { it.id }) { node ->
                        NodeRow(
                            node,
                            selected = node.id == Store.activeId,
                            ping = snap.delays[ConfigBuilder.tagOf(node)],
                        ) {
                            if (node.supported) select(context, node.id)
                            else Toast.makeText(context, "xhttp пока не поддержан (xray, M3)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        // FAB-молния: перетест пинга всего профиля (как на desktop). Только при туннеле.
        if (connected && nodes.size >= 1) {
            TestAllFab(
                testing = snap.testing,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            ) { ClashMonitor.urlTestAll() }
        }
    }
}

/** Выбрать узел/режим и, если туннель уже поднят, перестроить его на лету. */
private fun select(context: Context, id: String) {
    Store.setActive(id)
    // только Connected: при Connecting commandServer ещё null → reload-интент стартовал бы заново.
    if (VpnController.state == ConnState.Connected) NinetyVpnService.reload(context)
}

private fun nodeByTag(nodes: List<Node>, tag: String?): Node? {
    if (tag == null) return null
    return nodes.firstOrNull { ConfigBuilder.tagOf(it) == tag }
}

/** Круглый FAB-перетест: молния, при работе — вращается. */
@Composable
private fun TestAllFab(testing: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pack = NinetyState.pack
    val spin = rememberInfiniteTransition(label = "fabspin")
    val angle by spin.animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "ang",
    )
    Box(
        modifier
            .size(52.dp)
            .background(pack.accent, CircleShape)
            .border(1.dp, pack.accentBright, CircleShape)
            .clickable(enabled = !testing) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            NinetyIcons.Nodes, contentDescription = "Перетестировать ноды",
            tint = Ink.Ink0,
            modifier = Modifier.size(22.dp).rotate(if (testing) angle else 0f),
        )
    }
}

/** Строка автовыбора — урлтест по всем нодам профиля (как «auto» в desktop). */
@Composable
private fun AutoRow(
    selected: Boolean,
    effectiveName: String?,
    ping: Int?,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) pack.accentSoft else Ink.Ink1, RoundedCornerShape(14.dp))
            .border(1.dp, if (selected) pack.accent else Ink.Line2, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).background(pack.accentSoft, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(NinetyIcons.Nodes, contentDescription = null, tint = pack.accentBright, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Авто", color = Ink.TextHi, style = NinetyTypography.titleMedium)
            Text(
                effectiveName?.let { "Сейчас → $it" } ?: "Быстрейший узел по задержке",
                color = if (effectiveName != null) pack.accentBright else Ink.TextLo,
                style = MonoStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        PingPill(ping)
    }
}

@Composable
private fun NodeRow(node: Node, selected: Boolean, ping: Int?, onClick: () -> Unit) {
    val pack = NinetyState.pack
    val enabled = node.supported
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) pack.accentSoft else Ink.Ink1, RoundedCornerShape(14.dp))
            .border(1.dp, if (selected) pack.accent else Ink.Line2, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(10.dp).background(if (selected) pack.accent else Ink.Line3, CircleShape)
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                node.name.ifBlank { node.host },
                color = if (enabled) Ink.TextHi else Ink.TextLo,
                style = NinetyTypography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${node.proto} · ${node.host}:${node.port}" + if (!enabled) " · xray (M3)" else "",
                color = Ink.TextLo, style = MonoStyle,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(10.dp))
        // xhttp-ноды движок M2 не тестирует → пинга нет; для них пилюлю гасим.
        if (enabled) PingPill(ping) else Text("M3", style = MonoStyle, color = Ink.TextFaint)
    }
}

private fun plural(n: Int): String {
    val mod10 = n % 10; val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "нода"
        mod10 in 2..4 && mod100 !in 12..14 -> "ноды"
        else -> "нод"
    }
}
