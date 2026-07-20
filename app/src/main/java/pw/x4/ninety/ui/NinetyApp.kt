package pw.x4.ninety.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pw.x4.ninety.BuildConfig
import pw.x4.ninety.data.Fmt
import pw.x4.ninety.data.Store
import pw.x4.ninety.data.Updater
import pw.x4.ninety.ui.components.DesktopActiveNavShape
import pw.x4.ninety.ui.components.DesktopBackdrop
import pw.x4.ninety.ui.components.DesktopBrandMark
import pw.x4.ninety.ui.components.DesktopRowShape
import pw.x4.ninety.ui.components.UpdateModal
import pw.x4.ninety.ui.components.desktopCard
import pw.x4.ninety.ui.components.desktopNavRow
import pw.x4.ninety.ui.components.desktopSidebar
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.layout.NinetyLayoutMetrics
import pw.x4.ninety.ui.layout.NinetyWindowClass
import pw.x4.ninety.ui.layout.layoutMetrics
import pw.x4.ninety.ui.screens.HomeScreen
import pw.x4.ninety.ui.screens.NodesScreen
import pw.x4.ninety.ui.screens.ProfilesScreen
import pw.x4.ninety.ui.screens.SettingsScreen
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.vpn.ClashMonitor
import pw.x4.ninety.vpn.ConnState
import pw.x4.ninety.vpn.TunnelModes
import pw.x4.ninety.vpn.VpnController

private enum class Dest(val label: String, val kicker: String, val icon: ImageVector) {
    Home("Главная", "Overview", NinetyIcons.Home),
    Profiles("Профили", "Subscriptions", NinetyIcons.Profiles),
    Nodes("Ноды", "Proxy fleet", NinetyIcons.Nodes),
    Settings("Настройки", "System", NinetyIcons.Settings),
}

@Composable
fun NinetyApp(onToggleVpn: () -> Unit) {
    var dest by rememberSaveable { mutableStateOf(Dest.Home) }

    DesktopBackdrop {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val metrics = layoutMetrics(maxWidth)
            if (metrics.windowClass == NinetyWindowClass.Compact) {
                CompactShell(dest, onSelect = { dest = it }) {
                    ScreenHost(metrics, dest, onToggleVpn, onNavigate = { dest = it })
                }
            } else {
                DesktopShell(metrics, dest, onSelect = { dest = it }) {
                    ScreenHost(metrics, dest, onToggleVpn, onNavigate = { dest = it })
                }
            }

            Updater.Available.release?.let { release ->
                UpdateModal(release, onDismiss = { Updater.Available.release = null })
            }
        }
    }
}

@Composable
private fun CompactShell(
    current: Dest,
    onSelect: (Dest) -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        containerColor = Color.Transparent,
        bottomBar = { CompactBottomBar(current, onSelect) },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) { content() }
    }
}

@Composable
private fun DesktopShell(
    metrics: NinetyLayoutMetrics,
    current: Dest,
    onSelect: (Dest) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        DesktopSidebar(metrics, current, onSelect)
        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}

@Composable
private fun ScreenHost(
    metrics: NinetyLayoutMetrics,
    dest: Dest,
    onToggleVpn: () -> Unit,
    onNavigate: (Dest) -> Unit,
) {
    when (dest) {
        Dest.Home -> HomeScreen(
            metrics = metrics,
            onToggle = onToggleVpn,
            onOpenProfiles = { onNavigate(Dest.Profiles) },
            onOpenNodes = { onNavigate(Dest.Nodes) },
        )
        Dest.Profiles -> ProfilesScreen(metrics)
        Dest.Nodes -> NodesScreen(metrics)
        Dest.Settings -> SettingsScreen(metrics)
    }
}

@Composable
private fun DesktopSidebar(
    metrics: NinetyLayoutMetrics,
    current: Dest,
    onSelect: (Dest) -> Unit,
) {
    val expanded = metrics.windowClass == NinetyWindowClass.Expanded
    Column(
        Modifier
            .width(metrics.navigationWidth)
            .fillMaxHeight()
            .desktopSidebar(),
    ) {
        DesktopBrand(expanded)
        SidebarStateStrip(expanded)
        Column(Modifier.fillMaxWidth()) {
            Dest.entries.forEach { destination ->
                DesktopNavRow(
                    destination = destination,
                    active = destination == current,
                    expanded = expanded,
                    onClick = { onSelect(destination) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (expanded) DesktopRuntimePanel() else CompactRailRuntime()
    }
}

@Composable
private fun DesktopBrand(expanded: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (expanded) 126.dp else 88.dp)
            .padding(horizontal = if (expanded) 20.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        DesktopBrandMark(Modifier.size(if (expanded) 94.dp else 54.dp))
        if (expanded) {
            Spacer(Modifier.width(15.dp))
            Column {
                Text(
                    "NINETY",
                    style = NinetyTypography.headlineMedium.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 30.sp,
                        lineHeight = 30.sp,
                        letterSpacing = 3.75.sp,
                    ),
                    color = Ink.TextHi,
                    maxLines = 1,
                )
                Spacer(Modifier.height(9.dp))
                Text("190X4 · VPN", style = KickerStyle, color = Ink.TextFaint, maxLines = 1)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line2))
}

@Composable
private fun SidebarStateStrip(expanded: Boolean) {
    val state = VpnController.state
    val color = when (state) {
        ConnState.Connected -> NinetyState.pack.material.status
        ConnState.Connecting, ConnState.Stopping -> NinetyState.pack.accentBright
        ConnState.Idle -> Ink.TextFaint
    }
    val label = when (state) {
        ConnState.Idle -> "ENGINE STANDBY"
        ConnState.Connecting -> "VERIFYING TUN"
        ConnState.Connected -> "TUNNEL ONLINE"
        ConnState.Stopping -> "DISCONNECTING"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(NinetyState.pack.material.rowMiddle.copy(alpha = 0.78f))
            .border(1.dp, Ink.Line1)
            .padding(horizontal = if (expanded) 22.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        if (expanded) {
            Spacer(Modifier.width(9.dp))
            Text(label, style = KickerStyle, color = Ink.TextLo, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text("v${BuildConfig.VERSION_NAME}", style = MonoStyle, color = Ink.TextFaint)
        }
    }
}

@Composable
private fun DesktopNavRow(
    destination: Dest,
    active: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val shape: Shape = if (active) DesktopActiveNavShape else DesktopRowShape
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (expanded) 66.dp else 58.dp)
            .clip(shape)
            .desktopNavRow(active, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(start = if (expanded) 26.dp else 0.dp, end = if (expanded) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Icon(
            destination.icon,
            destination.label,
            tint = if (active) NinetyState.pack.accentBright else NinetyState.pack.material.sidebarText,
            modifier = Modifier.size(22.dp),
        )
        if (expanded) {
            Spacer(Modifier.width(19.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    destination.label,
                    style = NinetyTypography.titleMedium,
                    color = if (active) NinetyState.pack.material.sidebarTextActive else NinetyState.pack.material.sidebarText,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    destination.kicker.uppercase(),
                    style = KickerStyle,
                    color = if (active) NinetyState.pack.material.secondary else Ink.TextFaint,
                )
            }
            if (active) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(40.dp)
                        .background(NinetyState.pack.accent),
                )
            }
        }
    }
}

@Composable
private fun DesktopRuntimePanel() {
    val monitor = ClashMonitor.snapshot
    val (downValue, downUnit) = Fmt.rate(monitor.down)
    val (upValue, upUnit) = Fmt.rate(monitor.up)
    Column(
        Modifier
            .fillMaxWidth()
            .desktopCard(shape = RoundedCornerShape(0.dp))
            .padding(18.dp),
    ) {
        Text("TRAFFIC · LIVE", style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(11.dp))
        SidebarMetric("↓", downValue, downUnit, NinetyState.pack.accentBright)
        Spacer(Modifier.height(7.dp))
        SidebarMetric("↑", upValue, upUnit, Ink.TextMid)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line1))
        Spacer(Modifier.height(10.dp))
        Text(TunnelModes.current().title.uppercase(), style = KickerStyle, color = NinetyState.pack.material.secondary)
        Spacer(Modifier.height(4.dp))
        Text(
            TunnelModes.activeLabel() ?: Store.activeProfile()?.name ?: "Маршрут не готов",
            style = MonoStyle,
            color = Ink.TextLo,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SidebarMetric(arrow: String, value: String, unit: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(arrow, style = MonoStyle, color = color)
        Spacer(Modifier.width(8.dp))
        Text(value, style = NinetyTypography.titleMedium, color = Ink.TextHi)
        Spacer(Modifier.width(4.dp))
        Text(unit, style = KickerStyle, color = Ink.TextLo)
    }
}

@Composable
private fun CompactRailRuntime() {
    val active = VpnController.state == ConnState.Connected
    Box(
        Modifier.fillMaxWidth().height(58.dp).desktopCard(shape = RoundedCornerShape(0.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            NinetyIcons.Shield,
            contentDescription = null,
            tint = if (active) NinetyState.pack.material.status else Ink.TextFaint,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun CompactBottomBar(current: Dest, onSelect: (Dest) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .desktopCard(shape = RoundedCornerShape(0.dp))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Dest.entries.forEach { destination ->
            val active = destination == current
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(NinetyRadius.sm))
                    .background(if (active) NinetyState.pack.accentSoft else Color.Transparent)
                    .border(
                        1.dp,
                        if (active) NinetyState.pack.material.border else Color.Transparent,
                        RoundedCornerShape(NinetyRadius.sm),
                    )
                    .clickable { onSelect(destination) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    destination.icon,
                    destination.label,
                    tint = if (active) NinetyState.pack.accentBright else Ink.TextLo,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    destination.label,
                    style = KickerStyle,
                    color = if (active) NinetyState.pack.material.secondary else Ink.TextFaint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
