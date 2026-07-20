package pw.x4.ninety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pw.x4.ninety.data.Fmt
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.components.desktopCard
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.vpn.ClashMonitor
import pw.x4.ninety.vpn.ConfigBuilder
import pw.x4.ninety.vpn.ConnState
import pw.x4.ninety.vpn.TunnelMode
import pw.x4.ninety.vpn.TunnelModes
import pw.x4.ninety.vpn.VpnController
import pw.x4.ninety.vpn.WarpRuntime

@Composable
internal fun DesktopActiveRouteCard(
    target: String?,
    delay: Int?,
    onClick: () -> Unit,
) {
    val node = Store.activeNode()
    val mode = TunnelModes.current()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NinetyRadius.md))
            .desktopCard(active = VpnController.state == ConnState.Connected)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(Ink.Ink2, RoundedCornerShape(NinetyRadius.sm))
                .border(1.dp, Ink.Line1, RoundedCornerShape(NinetyRadius.sm)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(NinetyIcons.Nodes, null, tint = NinetyState.pack.accentBright, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                target ?: if (mode == TunnelMode.WARP_DIRECT) "Cloudflare WARP" else "Маршрут не выбран",
                style = NinetyTypography.titleMedium,
                color = Ink.TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when (mode) {
                    TunnelMode.PROXY -> "${node?.proto?.uppercase() ?: "AUTO"} · ${node?.host ?: Store.activeProfile()?.name ?: "NO PROFILE"}"
                    TunnelMode.WARP_DIRECT -> if (WarpRuntime.snapshot.registered) "WARP DIRECT · REGISTERED" else "WARP · REGISTRATION REQUIRED"
                    TunnelMode.WARP_CHAIN -> "${node?.proto?.uppercase() ?: "AUTO"} → CLOUDFLARE WARP"
                },
                style = KickerStyle,
                color = Ink.TextLo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                delay?.takeIf { it in 1 until 65_000 }?.let { "$it" } ?: "—",
                style = NinetyTypography.titleMedium,
                color = desktopPingColor(delay),
            )
            Text("PING · MS", style = KickerStyle, color = Ink.TextFaint)
        }
        Spacer(Modifier.width(12.dp))
        Icon(NinetyIcons.ChevronRight, null, tint = Ink.TextFaint, modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun DesktopTelemetryStrip(
    compact: Boolean,
    sessionSeconds: Int,
) {
    val monitor = ClashMonitor.snapshot
    val mode = TunnelModes.current()
    val connected = VpnController.state == ConnState.Connected
    val cells = listOf(
        "СЕРВЕР" to (desktopEffectiveNodeName(monitor.autoNow) ?: "—"),
        "ПИНГ" to (monitor.effectiveDelay()?.takeIf { it in 1 until 65_000 }?.let { "$it мс" } ?: "—"),
        "КАНАЛ" to "${Fmt.rate(monitor.down).first} ${Fmt.rate(monitor.down).second}",
        "СЕССИЯ" to desktopSessionText(sessionSeconds),
        "РЕЖИМ" to mode.title,
    )

    if (compact) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NinetyRadius.md))
                .desktopCard()
                .padding(horizontal = 13.dp, vertical = 11.dp),
        ) {
            cells.chunked(2).forEachIndexed { index, rowCells ->
                if (index > 0) {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line1))
                    Spacer(Modifier.height(10.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowCells.forEach { (label, value) ->
                        DesktopTelemetryCell(label, if (connected) value else "—", Modifier.weight(1f))
                    }
                    if (rowCells.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NinetyRadius.md))
                .desktopCard()
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            cells.forEachIndexed { index, (label, value) ->
                if (index > 0) Box(Modifier.width(1.dp).height(34.dp).background(Ink.Line1))
                DesktopTelemetryCell(
                    label,
                    if (connected) value else "—",
                    Modifier.weight(1f).padding(horizontal = 13.dp),
                )
            }
        }
    }
}

@Composable
private fun DesktopTelemetryCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(5.dp))
        Text(value, style = MonoStyle, color = Ink.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun DesktopErrorStrip(message: String, warning: Boolean = false) {
    Text(
        message,
        style = MonoStyle,
        color = if (warning) Ink.Warn else Ink.Err,
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.Ink1)
            .border(1.dp, if (warning) Ink.Warn.copy(alpha = 0.25f) else Ink.Err.copy(alpha = 0.25f))
            .padding(horizontal = 13.dp, vertical = 10.dp),
    )
}

internal fun desktopHeroStageSize(compact: Boolean, available: Dp): Dp {
    val max = if (compact) 330.dp else 380.dp
    return available.coerceAtMost(max).coerceAtLeast(230.dp)
}

internal fun desktopEffectiveNodeName(autoTag: String?): String? {
    val mode = TunnelModes.current()
    if (mode == TunnelMode.WARP_DIRECT) return "Cloudflare WARP"
    if (!Store.isAutoActive) return Store.activeNodeLabel()
    val effective = autoTag?.let { tag ->
        Store.activeProfileNodes().firstOrNull { ConfigBuilder.tagOf(it) == tag }
    }
    return effective?.name?.let { "Auto · $it" } ?: "Auto"
}

private fun desktopPingColor(value: Int?): Color = when {
    value == null || value <= 0 || value >= 65_000 -> Ink.TextLo
    value < 800 -> Ink.Ok
    value < 1500 -> Ink.Warn
    else -> Ink.Err
}

private fun desktopSessionText(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
