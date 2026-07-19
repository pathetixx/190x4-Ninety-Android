package pw.x4.ninety.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pw.x4.ninety.data.Fmt
import pw.x4.ninety.data.Profile
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.components.Hero
import pw.x4.ninety.ui.components.IconTile
import pw.x4.ninety.ui.components.SurfaceCard
import pw.x4.ninety.ui.components.premiumCard
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
import pw.x4.ninety.vpn.VpnController

@Composable
fun HomeScreen(
    metrics: NinetyLayoutMetrics,
    onToggle: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenNodes: () -> Unit,
) {
    NinetyPage(metrics) {
        if (metrics.isExpanded) {
            ExpandedHome(onToggle, onOpenProfiles, onOpenNodes)
        } else {
            CompactHome(metrics, onToggle, onOpenProfiles, onOpenNodes)
        }
    }
}

@Composable
private fun CompactHome(
    metrics: NinetyLayoutMetrics,
    onToggle: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenNodes: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileTile(onOpenProfiles)
        Spacer(Modifier.height(if (metrics.isCompact) 18.dp else 24.dp))
        HeroStatus(
            stageSize = if (metrics.isCompact) 252.dp else 286.dp,
            onToggle = onToggle,
        )
        Spacer(Modifier.height(if (metrics.isCompact) 22.dp else 28.dp))
        LocationTile(onOpenNodes)
        if (!metrics.isCompact) {
            Spacer(Modifier.height(14.dp))
            LiveSessionCard()
        }
    }
}

@Composable
private fun ExpandedHome(
    onToggle: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenNodes: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp, bottom = 28.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CONTROL · 190X4", style = KickerStyle, color = Ink.TextFaint)
                Spacer(Modifier.height(5.dp))
                Text("Центр подключения", style = NinetyTypography.headlineMedium, color = Ink.TextHi)
            }
            val profile = Store.activeProfile()
            Text(
                profile?.name ?: "ПРОФИЛЬ НЕ ВЫБРАН",
                style = MonoStyle,
                color = if (profile == null) Ink.TextLo else NinetyState.pack.accentBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                HeroStatus(stageSize = 328.dp, onToggle = onToggle)
            }
            Column(
                Modifier.widthIn(min = 350.dp, max = 410.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ProfileTile(onOpenProfiles)
                LocationTile(onOpenNodes)
                LiveSessionCard()
            }
        }
    }
}

@Composable
private fun HeroStatus(stageSize: androidx.compose.ui.unit.Dp, onToggle: () -> Unit) {
    val pack = NinetyState.pack
    val state = VpnController.state
    val snapshot = ClashMonitor.snapshot
    val secured = state == ConnState.Connected
    val title = when (state) {
        ConnState.Idle -> "Не защищено"
        ConnState.Connecting -> "Подключение…"
        ConnState.Connected -> "Защищено"
        ConnState.Stopping -> "Отключение…"
    }
    val hint = when (state) {
        ConnState.Idle -> "STAND-BY · DISCONNECTED"
        ConnState.Connecting -> "LINKING · NEGOTIATING"
        ConnState.Connected -> "SECURED · TUNNEL ACTIVE"
        ConnState.Stopping -> "STAND-BY · DISCONNECTING"
    }

    val textIn = remember { Animatable(0f) }
    LaunchedEffect(state) {
        textIn.snapTo(0f)
        textIn.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    val pulse = rememberInfiniteTransition(label = "homeStatusPulse")
    val dotAlpha by pulse.animateFloat(
        1f,
        0.4f,
        infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "homeStatusDot",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Hero(state = state, stageSize = stageSize, onToggle = onToggle)
        Spacer(Modifier.height(22.dp))
        Text(
            title,
            style = NinetyTypography.headlineMedium,
            color = if (secured) pack.accentBright else Ink.TextHi,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                alpha = textIn.value
                translationY = (1f - textIn.value) * 14f
            },
        )
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background((if (secured) pack.accent else Ink.TextLo).copy(alpha = dotAlpha)),
            )
            Spacer(Modifier.width(8.dp))
            Text(hint, style = KickerStyle, color = Ink.TextLo)
        }
        if (secured) {
            Spacer(Modifier.height(13.dp))
            HeroPing(snapshot.effectiveDelay(), snapshot.testing) { ClashMonitor.urlTestAll() }
        }
        VpnController.lastError?.let { error ->
            if (state == ConnState.Idle) {
                Spacer(Modifier.height(10.dp))
                Text(error, style = MonoStyle, color = Ink.Err, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun HeroPing(ms: Int?, testing: Boolean, onClick: () -> Unit) {
    val dead = ms == null || ms <= 0 || ms >= 65000
    val color = when {
        dead -> Ink.TextLo
        ms!! < 800 -> Ink.Ok
        ms < 1500 -> Ink.Warn
        else -> Ink.Err
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Ink.Ink2)
            .border(1.dp, Ink.Line2, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            NinetyIcons.Wifi,
            contentDescription = "Обновить задержку",
            tint = if (testing) NinetyState.pack.accentBright else color,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(if (dead) "—" else "$ms", style = NinetyTypography.titleMedium, color = color)
        Spacer(Modifier.width(3.dp))
        Text("МС", style = KickerStyle, color = color.copy(alpha = 0.7f))
    }
}

@Composable
private fun ProfileTile(onClick: () -> Unit) {
    val pack = NinetyState.pack
    val profile: Profile? = Store.activeProfile()
    Row(
        Modifier
            .fillMaxWidth()
            .premiumCard()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(NinetyIcons.Globe)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (profile == null) {
                Text("Профиль не выбран", style = NinetyTypography.titleMedium, color = Ink.TextHi)
                Spacer(Modifier.height(2.dp))
                Text("Нажмите, чтобы добавить подписку", style = MonoStyle, color = Ink.TextLo)
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.name,
                        style = NinetyTypography.titleMedium,
                        color = Ink.TextHi,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    profile.daysLeft()?.let { days ->
                        Spacer(Modifier.width(10.dp))
                        Text("$days ДН", style = KickerStyle, color = pack.accentBright)
                    }
                }
                profile.usedFraction()?.let { fraction ->
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Ink.Line1),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(2.dp)
                                .background(pack.accent, RoundedCornerShape(1.dp)),
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                val count = Store.nodeCount(profile.id)
                Text(
                    if (profile.isSub) "$count ${plural(count)}" else "Одиночный конфиг",
                    style = MonoStyle,
                    color = Ink.TextLo,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(NinetyIcons.ChevronRight, null, tint = Ink.TextFaint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun LocationTile(onClick: () -> Unit) {
    val pack = NinetyState.pack
    val auto = Store.isAutoActive
    val node = Store.activeNode()
    val snapshot = ClashMonitor.snapshot
    val connected = VpnController.state == ConnState.Connected
    val effectiveNode = if (auto) {
        snapshot.autoNow?.let { tag ->
            Store.activeProfileNodes().firstOrNull { ConfigBuilder.tagOf(it) == tag }
        }
    } else {
        null
    }

    Row(
        Modifier
            .fillMaxWidth()
            .premiumCard()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (auto || node != null) pack.accent else Ink.Line3),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    auto -> "Авто"
                    node != null -> node.name.ifBlank { node.host }
                    else -> "Сервер не выбран"
                },
                style = NinetyTypography.titleMedium,
                color = Ink.TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = when {
                auto -> effectiveNode?.let { "→ ${it.name.ifBlank { it.host }}" } ?: "БЫСТРЕЙШИЙ УЗЕЛ"
                node != null -> "${node.proto.uppercase()} · ${if (node.security != "none") node.security.uppercase() else node.host}"
                else -> null
            }
            subtitle?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    it,
                    style = KickerStyle,
                    color = if (auto && effectiveNode != null) pack.accentBright else Ink.TextLo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (connected) {
            Spacer(Modifier.width(8.dp))
            TrafficMini(snapshot.down, snapshot.up)
        }
        Spacer(Modifier.width(8.dp))
        Icon(NinetyIcons.ChevronRight, null, tint = Ink.TextFaint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun LiveSessionCard() {
    val snapshot = ClashMonitor.snapshot
    val state = VpnController.state
    val connected = state == ConnState.Connected
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SESSION · LIVE", style = KickerStyle, color = Ink.TextFaint)
                Spacer(Modifier.height(5.dp))
                Text(
                    if (connected) VpnController.activeServer ?: "Активный туннель" else "Сессия не запущена",
                    style = NinetyTypography.titleMedium,
                    color = if (connected) Ink.TextHi else Ink.TextLo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                NinetyIcons.Shield,
                null,
                tint = if (connected) NinetyState.pack.accentBright else Ink.TextFaint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricCell("PING", snapshot.effectiveDelay()?.takeIf { it in 1..64999 }?.let { "$it мс" } ?: "—")
            MetricCell("DOWN", formatRate(snapshot.down))
            MetricCell("UP", formatRate(snapshot.up))
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String) {
    Column {
        Text(label, style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MonoStyle, color = Ink.TextHi)
    }
}

@Composable
private fun TrafficMini(down: Long, up: Long) {
    val (downValue, downUnit) = Fmt.rate(down)
    val (upValue, upUnit) = Fmt.rate(up)
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("↓ ", style = MonoStyle, color = NinetyState.pack.accentBright)
            Text(downValue, style = MonoStyle, color = Ink.TextHi)
            Text(" $downUnit", style = KickerStyle, color = Ink.TextLo)
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("↑ ", style = MonoStyle, color = Ink.TextMid)
            Text(upValue, style = MonoStyle, color = Ink.TextHi)
            Text(" $upUnit", style = KickerStyle, color = Ink.TextLo)
        }
    }
}

private fun formatRate(value: Long): String {
    val (number, unit) = Fmt.rate(value)
    return "$number $unit"
}

private fun plural(value: Int): String {
    val mod10 = value % 10
    val mod100 = value % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "нода"
        mod10 in 2..4 && mod100 !in 12..14 -> "ноды"
        else -> "нод"
    }
}
