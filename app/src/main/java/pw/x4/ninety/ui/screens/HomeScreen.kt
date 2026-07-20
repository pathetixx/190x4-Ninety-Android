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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pw.x4.ninety.data.Fmt
import pw.x4.ninety.data.Store
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
import pw.x4.ninety.vpn.ProbePhase
import pw.x4.ninety.vpn.TunnelMode
import pw.x4.ninety.vpn.TunnelModes
import pw.x4.ninety.vpn.VpnController
import pw.x4.ninety.vpn.WarpRuntime

@Composable
fun HomeScreen(
    metrics: NinetyLayoutMetrics,
    onToggle: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenNodes: () -> Unit,
) {
    NinetyPage(metrics) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = if (metrics.isCompact) 16.dp else 24.dp, bottom = 30.dp),
        ) {
            HomeHeader()
            Spacer(Modifier.height(20.dp))
            TunnelModePanel()
            Spacer(Modifier.height(14.dp))
            if (metrics.isExpanded) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    ConnectionPanel(onToggle, Modifier.weight(1.35f))
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        RouteCard(onOpenNodes)
                        ProfileCard(onOpenProfiles)
                        TrafficCard()
                    }
                }
            } else {
                ConnectionPanel(onToggle)
                Spacer(Modifier.height(14.dp))
                RouteCard(onOpenNodes)
                Spacer(Modifier.height(14.dp))
                ProfileCard(onOpenProfiles)
                Spacer(Modifier.height(14.dp))
                TrafficCard()
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("NINETY · CONTROL", style = KickerStyle, color = Ink.TextFaint)
            Spacer(Modifier.height(5.dp))
            Text("Подключение", style = NinetyTypography.headlineMedium, color = Ink.TextHi)
            Spacer(Modifier.height(5.dp))
            Text(
                "Режим, маршрут и состояние туннеля — без скрытых переключений.",
                style = NinetyTypography.bodyMedium,
                color = Ink.TextMid,
            )
        }
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NinetyState.pack.accentSoft)
                .border(1.dp, NinetyState.pack.accentSoft, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("九", style = NinetyTypography.titleLarge, color = NinetyState.pack.accentBright)
        }
    }
}

@Composable
private fun TunnelModePanel() {
    val context = LocalContext.current
    val current = TunnelModes.current()
    val active = VpnController.isActive
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.Ink1)
            .border(1.dp, Ink.Line1, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("РЕЖИМ ТУННЕЛЯ", style = KickerStyle, color = Ink.TextFaint)
            Spacer(Modifier.weight(1f))
            if (active) Text("Остановите VPN для смены", style = MonoStyle, color = Ink.Warn)
        }
        Spacer(Modifier.height(11.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TunnelMode.entries.forEach { mode ->
                ModeButton(
                    mode = mode,
                    selected = mode == current,
                    enabled = !active,
                    modifier = Modifier.weight(1f),
                ) { TunnelModes.select(context, mode) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when (current) {
                TunnelMode.PROXY -> "Трафик идёт через выбранную Ninety-ноду или штатный Auto sing-box."
                TunnelMode.WARP_DIRECT -> if (WarpRuntime.snapshot.registered) {
                    "Трафик идёт напрямую через Cloudflare WARP. Proxy-профиль не требуется."
                } else {
                    "Требуется регистрация: Настройки → Маршрутизация → WARP."
                }
                TunnelMode.WARP_CHAIN -> if (WarpRuntime.snapshot.registered) {
                    "Первый hop — Ninety-нода, второй hop — Cloudflare WARP."
                } else {
                    "Требуется регистрация WARP и выбранная Ninety-нода."
                }
            },
            style = MonoStyle,
            color = if (current.usesWarp && !WarpRuntime.snapshot.registered) Ink.Warn else Ink.TextLo,
        )
    }
}

@Composable
private fun ModeButton(
    mode: TunnelMode,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    Column(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) pack.accentSoft else Ink.Ink2)
            .border(1.dp, if (selected) pack.accent else Ink.Line1, RoundedCornerShape(11.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(mode.title, style = NinetyTypography.titleMedium, color = if (selected) pack.accentBright else Ink.TextMid)
        Spacer(Modifier.height(3.dp))
        Text(
            when (mode) {
                TunnelMode.PROXY -> "NODE"
                TunnelMode.WARP_DIRECT -> "DIRECT"
                TunnelMode.WARP_CHAIN -> "2 HOPS"
            },
            style = KickerStyle,
            color = if (selected) pack.accent else Ink.TextFaint,
            maxLines = 1,
        )
    }
}

@Composable
private fun ConnectionPanel(onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val state = VpnController.state
    val monitor = ClashMonitor.snapshot
    val mode = TunnelModes.current()
    val check = TunnelModes.checkStart(mode)
    val pack = NinetyState.pack
    val connected = state == ConnState.Connected
    val status = when (state) {
        ConnState.Idle -> "Отключено"
        ConnState.Connecting -> "Поднимаю TUN…"
        ConnState.Connected -> "Туннель активен"
        ConnState.Stopping -> "Останавливаю…"
    }
    val phase = when {
        state == ConnState.Connecting -> "ENGINE · VERIFYING TUN"
        connected && mode == TunnelMode.WARP_DIRECT -> "WARP · DIRECT"
        connected && monitor.phase == ProbePhase.Testing -> "PING · TESTING"
        connected && monitor.phase == ProbePhase.Error -> "PING · ERROR"
        connected -> "ENGINE · ONLINE"
        else -> "ENGINE · STANDBY"
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    0f to if (connected) pack.accentSoft else Ink.Ink1,
                    0.42f to Ink.Ink1,
                    1f to Ink.Ink0,
                ),
            )
            .border(1.dp, if (connected) pack.accentSoft else Ink.Line1, RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).background(
                    when (state) {
                        ConnState.Connected -> Ink.Ok
                        ConnState.Connecting, ConnState.Stopping -> Ink.Warn
                        ConnState.Idle -> Ink.TextFaint
                    },
                    CircleShape,
                ),
            )
            Spacer(Modifier.width(10.dp))
            Text(phase, style = KickerStyle, color = if (connected) pack.accentBright else Ink.TextLo)
        }
        Spacer(Modifier.height(22.dp))
        Text(status, style = NinetyTypography.headlineMedium, color = Ink.TextHi)
        Spacer(Modifier.height(8.dp))
        Text(
            routeDescription(mode, monitor.autoNow),
            style = NinetyTypography.bodyMedium,
            color = Ink.TextMid,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricBox("PING", pingText(monitor.effectiveDelay()), Modifier.weight(1f))
            MetricBox("DOWN", Fmt.rate(monitor.down).let { "${it.first} ${it.second}" }, Modifier.weight(1f))
            MetricBox("UP", Fmt.rate(monitor.up).let { "${it.first} ${it.second}" }, Modifier.weight(1f))
        }
        monitor.lastError?.takeIf { connected || state == ConnState.Idle }?.let { error ->
            Spacer(Modifier.height(12.dp))
            Text(error, style = MonoStyle, color = if (monitor.phase == ProbePhase.Error) Ink.Err else Ink.Warn)
        }
        VpnController.lastError?.let { error ->
            Spacer(Modifier.height(12.dp))
            Text(error, style = MonoStyle, color = Ink.Err)
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    when {
                        VpnController.isActive -> Ink.Err.copy(alpha = 0.16f)
                        check.allowed -> pack.accent
                        else -> Ink.Ink3
                    },
                )
                .border(
                    1.dp,
                    when {
                        VpnController.isActive -> Ink.Err.copy(alpha = 0.55f)
                        check.allowed -> pack.accentBright
                        else -> Ink.Line2
                    },
                    RoundedCornerShape(13.dp),
                )
                .clickable(enabled = VpnController.isActive || check.allowed) { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    VpnController.isActive -> "ОТКЛЮЧИТЬ"
                    check.allowed -> "ПОДКЛЮЧИТЬ"
                    else -> "РЕЖИМ НЕ ГОТОВ"
                },
                style = KickerStyle,
                color = when {
                    VpnController.isActive -> Ink.Err
                    check.allowed -> Ink.Ink0
                    else -> Ink.TextFaint
                },
                textAlign = TextAlign.Center,
            )
        }
        if (!check.allowed && !VpnController.isActive) {
            Spacer(Modifier.height(9.dp))
            Text(check.message.orEmpty(), style = MonoStyle, color = Ink.Warn, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(Ink.Ink2)
            .border(1.dp, Ink.Line1, RoundedCornerShape(11.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        Text(label, style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(5.dp))
        Text(value, style = NinetyTypography.titleMedium, color = Ink.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RouteCard(onClick: () -> Unit) {
    val mode = TunnelModes.current()
    val node = Store.activeNode()
    val auto = Store.isAutoActive
    val monitor = ClashMonitor.snapshot
    val effective = monitor.autoNow?.let { tag ->
        Store.activeProfileNodes().firstOrNull { ConfigBuilder.tagOf(it) == tag }
    }
    InfoCard(
        kicker = "ROUTE · ACTIVE",
        title = when (mode) {
            TunnelMode.WARP_DIRECT -> "Cloudflare WARP"
            TunnelMode.WARP_CHAIN -> (if (auto) effective?.name ?: "Auto" else node?.name ?: "Нода не выбрана") + " → WARP"
            TunnelMode.PROXY -> if (auto) effective?.name?.let { "Auto · $it" } ?: "Auto" else node?.name ?: "Нода не выбрана"
        },
        body = mode.description,
        icon = NinetyIcons.Nodes,
        onClick = onClick,
    )
}

@Composable
private fun ProfileCard(onClick: () -> Unit) {
    val profile = Store.activeProfile()
    InfoCard(
        kicker = "PROFILE",
        title = profile?.name ?: "Профиль не выбран",
        body = profile?.let { "${Store.nodeCount(it.id)} нод · ${if (it.isSub) "подписка" else "одиночный конфиг"}" }
            ?: "Добавьте подписку или одиночную ссылку",
        icon = NinetyIcons.Profiles,
        onClick = onClick,
    )
}

@Composable
private fun TrafficCard() {
    val monitor = ClashMonitor.snapshot
    val phase = monitor.phase
    InfoCard(
        kicker = "RUNTIME · LIVE",
        title = when (phase) {
            ProbePhase.Testing -> "Проверяю задержку"
            ProbePhase.Partial -> "Частичный результат"
            ProbePhase.Error -> "Ошибка ping-monitor"
            ProbePhase.Ready -> "Монитор готов"
            ProbePhase.Connecting -> "Подключаю monitor"
            ProbePhase.Offline -> "Монитор остановлен"
        },
        body = monitor.lastError ?: "Selector: ${monitor.selectorNow ?: "—"} · Auto: ${monitor.autoNow ?: "—"}",
        icon = NinetyIcons.Wifi,
    )
}

@Composable
private fun InfoCard(
    kicker: String,
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Ink.Ink1)
            .border(1.dp, Ink.Line1, RoundedCornerShape(15.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Ink.Ink2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = NinetyState.pack.accentBright, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(kicker, style = KickerStyle, color = Ink.TextFaint)
            Spacer(Modifier.height(4.dp))
            Text(title, style = NinetyTypography.titleMedium, color = Ink.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(body, style = MonoStyle, color = Ink.TextLo, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(NinetyIcons.ChevronRight, null, tint = Ink.TextFaint, modifier = Modifier.size(16.dp))
        }
    }
}

private fun routeDescription(mode: TunnelMode, autoTag: String?): String = when (mode) {
    TunnelMode.WARP_DIRECT -> "Android TUN → Cloudflare WARP → Internet"
    TunnelMode.WARP_CHAIN -> "Android TUN → ${Store.activeNodeLabel() ?: autoTag ?: "Auto"} → WARP → Internet"
    TunnelMode.PROXY -> "Android TUN → ${Store.activeNodeLabel() ?: autoTag ?: "Auto"} → Internet"
}

private fun pingText(value: Int?): String = value?.takeIf { it in 1 until 65_000 }?.let { "$it ms" } ?: "—"
