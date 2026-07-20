package pw.x4.ninety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pw.x4.ninety.data.Fmt
import pw.x4.ninety.data.Profile
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.components.Hero
import pw.x4.ninety.ui.components.desktopCard
import pw.x4.ninety.ui.components.topHairline
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
import pw.x4.ninety.vpn.ProbePhase
import pw.x4.ninety.vpn.TunnelMode
import pw.x4.ninety.vpn.TunnelModes
import pw.x4.ninety.vpn.VpnController
import pw.x4.ninety.vpn.WarpRuntime

/**
 * Desktop home composition port.
 *
 * Ordering intentionally follows `src/index.html`: subscription/tool row → Hero/HUD →
 * connection status → route card → reserved/active telemetry strip. On compact screens
 * the same hierarchy is kept and only the telemetry cells wrap.
 */
@Composable
fun HomeScreen(
    metrics: NinetyLayoutMetrics,
    onToggle: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenNodes: () -> Unit,
) {
    val state = VpnController.state
    val monitor = ClashMonitor.snapshot
    val profile = Store.activeProfile()
    val target = effectiveNodeName(monitor.autoNow)
    var sessionSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(state) {
        sessionSeconds = 0
        if (state == ConnState.Connected) {
            while (true) {
                delay(1000)
                sessionSeconds++
            }
        }
    }

    NinetyPage(metrics) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = if (metrics.isCompact) 14.dp else 20.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SubscriptionRow(
                profile = profile,
                onOpenProfiles = onOpenProfiles,
                onOpenNodes = onOpenNodes,
            )
            Spacer(Modifier.height(10.dp))
            TunnelModeStrip()
            Spacer(Modifier.height(if (metrics.isCompact) 18.dp else 8.dp))

            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val stage = heroStageSize(metrics.isCompact, maxWidth)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Hero(
                        state = state,
                        target = target,
                        stageSize = stage,
                        onToggle = onToggle,
                    )
                    Spacer(Modifier.height(if (metrics.isCompact) 4.dp else 8.dp))
                    HeroStatus(state = state, target = target)
                }
            }

            Spacer(Modifier.height(16.dp))
            ActiveRouteCard(
                target = target,
                delay = monitor.effectiveDelay(),
                onClick = onOpenNodes,
            )
            Spacer(Modifier.height(10.dp))
            TelemetryStrip(
                compact = metrics.isCompact,
                sessionSeconds = sessionSeconds,
            )

            VpnController.lastError?.let { error ->
                Spacer(Modifier.height(10.dp))
                ErrorStrip(error)
            }
            monitor.lastError?.takeIf { state == ConnState.Connected }?.let { error ->
                Spacer(Modifier.height(8.dp))
                ErrorStrip(error, warning = monitor.phase != ProbePhase.Error)
            }
        }
    }
}

@Composable
private fun SubscriptionRow(
    profile: Profile?,
    onOpenProfiles: () -> Unit,
    onOpenNodes: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 66.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 66.dp)
                .clip(RoundedCornerShape(NinetyRadius.md))
                .desktopCard()
                .clickable { onOpenProfiles() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(Ink.Ink2, RoundedCornerShape(NinetyRadius.sm))
                    .border(1.dp, Ink.Line1, RoundedCornerShape(NinetyRadius.sm)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    NinetyIcons.Profiles,
                    contentDescription = null,
                    tint = Ink.TextMid,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        profile?.name ?: "Добавьте профиль",
                        style = NinetyTypography.titleMedium,
                        color = Ink.TextHi,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    profile?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            quotaText(it),
                            style = KickerStyle,
                            color = Ink.TextLo,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                QuotaBar(profile?.usedFraction())
                Spacer(Modifier.height(6.dp))
                Text(
                    profile?.let {
                        "${Store.nodeCount(it.id)} нод · ${if (it.isSub) "SUBSCRIPTION" else "SINGLE CONFIG"}"
                    } ?: "SUBSCRIPTION · EMPTY",
                    style = KickerStyle,
                    color = Ink.TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            profile?.daysLeft()?.let { days ->
                Spacer(Modifier.width(14.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(days.toString(), style = NinetyTypography.titleMedium, color = NinetyState.pack.accentBright)
                    Text("ДНЕЙ", style = KickerStyle, color = Ink.TextFaint)
                }
            }
        }

        DesktopToolButton(NinetyIcons.Nodes, "Ноды", onOpenNodes)
        DesktopToolButton(NinetyIcons.Profiles, "Профили", onOpenProfiles)
    }
}

@Composable
private fun DesktopToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .width(46.dp)
            .heightIn(min = 66.dp)
            .clip(RoundedCornerShape(NinetyRadius.md))
            .desktopCard()
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Ink.TextMid, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun QuotaBar(fraction: Float?) {
    Box(Modifier.fillMaxWidth().height(2.dp).background(Ink.Line1)) {
        Box(
            Modifier
                .fillMaxWidth(fraction ?: if (Store.activeProfile() == null) 0f else 1f)
                .height(2.dp)
                .background(NinetyState.pack.accent),
        )
    }
}

@Composable
private fun TunnelModeStrip() {
    val context = LocalContext.current
    val current = TunnelModes.current()
    val active = VpnController.isActive

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .topHairline(alpha = if (NinetyState.pack.palette.isLight) 0.28f else 0.06f)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        TunnelMode.entries.forEach { mode ->
            val selected = mode == current
            Row(
                Modifier
                    .clip(RoundedCornerShape(NinetyRadius.xs))
                    .background(if (selected) NinetyState.pack.accentSoft else Ink.Ink1)
                    .border(
                        1.dp,
                        if (selected) NinetyState.pack.accent else Ink.Line1,
                        RoundedCornerShape(NinetyRadius.xs),
                    )
                    .clickable(enabled = !active) { TunnelModes.select(context, mode) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(5.dp).background(
                        if (selected) NinetyState.pack.accentBright else Ink.TextFaint,
                        CircleShape,
                    ),
                )
                Spacer(Modifier.width(7.dp))
                Text(mode.title.uppercase(), style = KickerStyle, color = if (selected) Ink.TextHi else Ink.TextLo)
                Spacer(Modifier.width(6.dp))
                Text(modeCode(mode), style = KickerStyle, color = if (selected) NinetyState.pack.accent else Ink.TextFaint)
            }
        }
        if (active) {
            Text(
                "STOP VPN TO CHANGE MODE",
                style = KickerStyle,
                color = Ink.Warn,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun HeroStatus(state: ConnState, target: String?) {
    val pack = NinetyState.pack
    val title = when (state) {
        ConnState.Idle -> "Готов к подключению"
        ConnState.Connecting -> "Устанавливаю защищённый канал"
        ConnState.Connected -> target ?: "Защищённое соединение"
        ConnState.Stopping -> "Останавливаю туннель"
    }
    val kicker = when (state) {
        ConnState.Idle -> "ENGINE · STANDBY"
        ConnState.Connecting -> "SYSTEM · LINKING"
        ConnState.Connected -> "SECURED · ${TunnelModes.current().title.uppercase()}"
        ConnState.Stopping -> "ENGINE · DISCONNECTING"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            style = NinetyTypography.headlineMedium,
            color = if (state == ConnState.Connected) pack.material.status else Ink.TextHi,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(6.dp).background(
                    when (state) {
                        ConnState.Connected -> pack.accentBright
                        ConnState.Connecting, ConnState.Stopping -> Ink.Warn
                        ConnState.Idle -> Ink.TextMid
                    },
                    CircleShape,
                ),
            )
            Spacer(Modifier.width(9.dp))
            Text(kicker, style = KickerStyle, color = Ink.TextLo)
        }
    }
}

@Composable
private fun ActiveRouteCard(
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
                color = pingColor(delay),
            )
            Text("PING · MS", style = KickerStyle, color = Ink.TextFaint)
        }
        Spacer(Modifier.width(12.dp))
        Icon(NinetyIcons.ChevronRight, null, tint = Ink.TextFaint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun TelemetryStrip(
    compact: Boolean,
    sessionSeconds: Int,
) {
    val monitor = ClashMonitor.snapshot
    val mode = TunnelModes.current()
    val connected = VpnController.state == ConnState.Connected
    val cells = listOf(
        "СЕРВЕР" to (effectiveNodeName(monitor.autoNow) ?: "—"),
        "ПИНГ" to (monitor.effectiveDelay()?.takeIf { it in 1 until 65_000 }?.let { "$it мс" } ?: "—"),
        "КАНАЛ" to "${Fmt.rate(monitor.down).first} ${Fmt.rate(monitor.down).second}",
        "СЕССИЯ" to sessionText(sessionSeconds),
        "РЕЖИМ" to mode.title,
    )

    if (compact) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(NinetyRadius.md)).desktopCard()
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
                        TelemetryCell(label, if (connected) value else "—", Modifier.weight(1f))
                    }
                    if (rowCells.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(NinetyRadius.md)).desktopCard()
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            cells.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    Box(Modifier.width(1.dp).height(34.dp).background(Ink.Line1))
                }
                TelemetryCell(label, if (connected) value else "—", Modifier.weight(1f).padding(horizontal = 13.dp))
            }
        }
    }
}

@Composable
private fun TelemetryCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(5.dp))
        Text(value, style = MonoStyle, color = Ink.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ErrorStrip(message: String, warning: Boolean = false) {
    Text(
        message,
        style = MonoStyle,
        color = if (warning) Ink.Warn else Ink.Err,
        modifier = Modifier.fillMaxWidth().background(Ink.Ink1).border(1.dp, if (warning) Ink.Warn.copy(alpha = 0.25f) else Ink.Err.copy(alpha = 0.25f))
            .padding(horizontal = 13.dp, vertical = 10.dp),
    )
}

private fun heroStageSize(compact: Boolean, available: Dp): Dp {
    val max = if (compact) 330.dp else 380.dp
    return available.coerceAtMost(max).coerceAtLeast(230.dp)
}

private fun effectiveNodeName(autoTag: String?): String? {
    val mode = TunnelModes.current()
    if (mode == TunnelMode.WARP_DIRECT) return "Cloudflare WARP"
    if (!Store.isAutoActive) return Store.activeNodeLabel()
    val effective = autoTag?.let { tag ->
        Store.activeProfileNodes().firstOrNull { ConfigBuilder.tagOf(it) == tag }
    }
    return effective?.name?.let { "Auto · $it" } ?: "Auto"
}

private fun quotaText(profile: Profile): String = if (profile.total > 0) {
    "${Fmt.bytes(profile.used)} / ${Fmt.bytes(profile.total)}"
} else {
    "UNLIMITED"
}

private fun modeCode(mode: TunnelMode): String = when (mode) {
    TunnelMode.PROXY -> "NODE"
    TunnelMode.WARP_DIRECT -> "DIRECT"
    TunnelMode.WARP_CHAIN -> "2 HOPS"
}

private fun pingColor(value: Int?): Color = when {
    value == null || value <= 0 || value >= 65_000 -> Ink.TextLo
    value < 800 -> Ink.Ok
    value < 1500 -> Ink.Warn
    else -> Ink.Err
}

private fun sessionText(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
