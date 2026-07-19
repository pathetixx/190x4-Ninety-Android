package pw.x4.ninety.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pw.x4.ninety.data.Fmt
import pw.x4.ninety.data.Store
import pw.x4.ninety.data.Updater
import pw.x4.ninety.ui.components.UpdateModal
import pw.x4.ninety.ui.components.topHairline
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
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.vpn.ClashMonitor
import pw.x4.ninety.vpn.ConnState
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

    BoxWithConstraints(Modifier.fillMaxSize().background(Ink.Ink0)) {
        val metrics = layoutMetrics(maxWidth)
        if (metrics.windowClass == NinetyWindowClass.Compact) {
            CompactShell(dest, onSelect = { dest = it }) {
                ScreenHost(metrics, dest, onToggleVpn, onNavigate = { dest = it })
            }
        } else {
            WideShell(metrics, dest, onSelect = { dest = it }) {
                ScreenHost(metrics, dest, onToggleVpn, onNavigate = { dest = it })
            }
        }

        Updater.Available.release?.let { release ->
            UpdateModal(release, onDismiss = { Updater.Available.release = null })
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
        containerColor = Ink.Ink0,
        bottomBar = { NinetyBottomBar(current, onSelect) },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) { content() }
    }
}

@Composable
private fun WideShell(
    metrics: NinetyLayoutMetrics,
    current: Dest,
    onSelect: (Dest) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        NinetySideNavigation(metrics, current, onSelect)
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
private fun NinetySideNavigation(
    metrics: NinetyLayoutMetrics,
    current: Dest,
    onSelect: (Dest) -> Unit,
) {
    val expanded = metrics.windowClass == NinetyWindowClass.Expanded
    Column(
        Modifier
            .width(metrics.navigationWidth)
            .fillMaxHeight()
            .background(Ink.Ink1)
            .border(width = 1.dp, color = Ink.Line1)
            .padding(horizontal = if (expanded) 14.dp else 10.dp, vertical = 16.dp),
    ) {
        BrandBlock(expanded)
        Spacer(Modifier.height(18.dp))
        SidebarState(expanded)
        Spacer(Modifier.height(18.dp))
        Dest.entries.forEach { destination ->
            SideNavItem(
                destination = destination,
                active = destination == current,
                expanded = expanded,
                onClick = { onSelect(destination) },
            )
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.weight(1f))
        if (expanded) TrafficPanel() else RailStatus()
    }
}

@Composable
private fun BrandBlock(expanded: Boolean) {
    val pack = NinetyState.pack
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    Brush.radialGradient(
                        0f to pack.accentSoft,
                        1f to Ink.Ink2,
                    ),
                )
                .border(1.dp, pack.accentSoft, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("九", style = NinetyTypography.titleLarge, color = pack.accentBright)
        }
        if (expanded) {
            Spacer(Modifier.width(12.dp))
            Column {
                Text("NINETY", style = NinetyTypography.titleLarge, color = Ink.TextHi)
                Text("190X4 · VPN", style = KickerStyle, color = Ink.TextLo)
            }
        }
    }
}

@Composable
private fun SidebarState(expanded: Boolean) {
    val state = VpnController.state
    val active = state == ConnState.Connected
    val label = when (state) {
        ConnState.Idle -> "Готов к подключению"
        ConnState.Connecting -> "Устанавливаю туннель"
        ConnState.Connected -> "Туннель защищён"
        ConnState.Stopping -> "Останавливаю туннель"
    }
    val color = if (active) NinetyState.pack.accent else Ink.TextLo
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Ink.Ink2)
            .border(1.dp, Ink.Line1, RoundedCornerShape(10.dp))
            .padding(horizontal = if (expanded) 12.dp else 0.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        if (expanded) {
            Spacer(Modifier.width(9.dp))
            Text(label, style = MonoStyle, color = Ink.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SideNavItem(
    destination: Dest,
    active: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    val tint by animateColorAsState(
        targetValue = if (active) pack.accentBright else Ink.TextLo,
        animationSpec = tween(180),
        label = "sideNavTint",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) pack.accentSoft else Color.Transparent)
            .border(1.dp, if (active) pack.accentSoft else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = if (expanded) 12.dp else 0.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Icon(destination.icon, destination.label, tint = tint, modifier = Modifier.size(21.dp))
        if (expanded) {
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(destination.label, style = NinetyTypography.titleMedium, color = if (active) Ink.TextHi else Ink.TextMid)
                Text(destination.kicker.uppercase(), style = KickerStyle, color = if (active) pack.accent else Ink.TextFaint)
            }
        }
    }
}

@Composable
private fun TrafficPanel() {
    val snapshot = ClashMonitor.snapshot
    val (downValue, downUnit) = Fmt.rate(snapshot.down)
    val (upValue, upUnit) = Fmt.rate(snapshot.up)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.Ink2)
            .border(1.dp, Ink.Line1, RoundedCornerShape(12.dp))
            .topHairline()
            .padding(13.dp),
    ) {
        Text("TRAFFIC · LIVE", style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(10.dp))
        TrafficRow("↓", downValue, downUnit, NinetyState.pack.accentBright)
        Spacer(Modifier.height(6.dp))
        TrafficRow("↑", upValue, upUnit, Ink.TextMid)
        Store.activeProfile()?.let { profile ->
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line1))
            Spacer(Modifier.height(9.dp))
            Text(profile.name, style = MonoStyle, color = Ink.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TrafficRow(arrow: String, value: String, unit: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(arrow, style = MonoStyle, color = color)
        Spacer(Modifier.width(8.dp))
        Text(value, style = NinetyTypography.titleMedium, color = Ink.TextHi)
        Spacer(Modifier.width(4.dp))
        Text(unit, style = KickerStyle, color = Ink.TextLo)
    }
}

@Composable
private fun RailStatus() {
    val active = VpnController.state == ConnState.Connected
    Box(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Ink.Ink2),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            NinetyIcons.Shield,
            contentDescription = null,
            tint = if (active) NinetyState.pack.accentBright else Ink.TextFaint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun NinetyBottomBar(current: Dest, onSelect: (Dest) -> Unit) {
    val pack = NinetyState.pack
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Ink.Ink0.copy(alpha = 0f),
                    0.35f to Ink.Ink1,
                    1f to Ink.Ink1,
                ),
            )
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line2))
        Row(
            Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dest.entries.forEach { destination ->
                val active = destination == current
                val tint by animateColorAsState(
                    if (active) pack.accent else Ink.TextLo,
                    tween(220),
                    label = "bottomNavTint",
                )
                Column(
                    Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(destination) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .height(2.dp)
                            .width(if (active) 18.dp else 0.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (active) pack.accent else Color.Transparent),
                    )
                    Spacer(Modifier.height(6.dp))
                    Icon(destination.icon, destination.label, tint = tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(
                        destination.label.uppercase(),
                        color = tint,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
