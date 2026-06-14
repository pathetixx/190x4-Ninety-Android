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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pw.x4.ninety.data.Fmt
import pw.x4.ninety.data.Profile
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.components.Hero
import pw.x4.ninety.ui.components.IconTile
import pw.x4.ninety.ui.components.premiumCard
import pw.x4.ninety.ui.icons.NinetyIcons
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
fun HomeScreen(onToggle: () -> Unit, onOpenProfiles: () -> Unit, onOpenNodes: () -> Unit) {
    val pack = NinetyState.pack
    val state = VpnController.state
    val snap = ClashMonitor.snapshot

    val title = when (state) {
        ConnState.Idle -> "Не защищено"
        ConnState.Connecting -> "Подключение…"
        ConnState.Connected -> "Защищено"
        ConnState.Stopping -> "Отключение…"
    }
    // Кикеры — десктоп-канон (main.js STATE_KICKER / CONNECTED_KICKER), латиницей.
    val hint = when (state) {
        ConnState.Idle -> "STAND-BY · DISCONNECTED"
        ConnState.Connecting -> "LINKING · NEGOTIATING"
        ConnState.Connected -> "SECURED · TUNNEL ACTIVE"
        ConnState.Stopping -> "STAND-BY · DISCONNECTING"
    }
    val secured = state == ConnState.Connected

    val textIn = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(state) {
        textIn.snapTo(0f); textIn.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    val pulse = rememberInfiniteTransition(label = "kpulse")
    val dotAlpha by pulse.animateFloat(
        1f, 0.4f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "dot",
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        ProfileTile(onClick = onOpenProfiles)

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Hero(state = state, onToggle = onToggle)
                Spacer(Modifier.height(24.dp))
                Text(
                    title,
                    style = NinetyTypography.headlineMedium,
                    color = if (secured) pack.accentBright else Ink.TextHi,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer {
                        alpha = textIn.value; translationY = (1f - textIn.value) * 14f
                    },
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(6.dp).clip(CircleShape)
                            .background((if (secured) pack.accent else Ink.TextLo).copy(alpha = dotAlpha))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(hint, style = KickerStyle, color = Ink.TextLo)
                }
                // Ping-пилюля под «Защищено» (порт desktop hero__ping): wifi-значок +
                // задержка эффективной ноды, клик = перетест. Только при туннеле.
                if (secured) {
                    Spacer(Modifier.height(14.dp))
                    HeroPing(ms = snap.effectiveDelay(), testing = snap.testing) {
                        ClashMonitor.urlTestAll()
                    }
                }
                VpnController.lastError?.let {
                    if (state == ConnState.Idle) {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MonoStyle, color = Ink.Err, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        LocationTile(onClick = onOpenNodes)
        Spacer(Modifier.height(12.dp))
    }
}

/** Ping-пилюля hero (wifi + задержка + МС, клик = перетест). Грейд цветом как desktop. */
@Composable
private fun HeroPing(ms: Int?, testing: Boolean, onClick: () -> Unit) {
    val pack = NinetyState.pack
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
        Icon(NinetyIcons.Wifi, contentDescription = "Обновить задержку",
            tint = if (testing) pack.accentBright else color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (dead) "—" else "$ms", style = NinetyTypography.titleMedium, color = color)
        Spacer(Modifier.width(3.dp))
        Text("МС", style = KickerStyle, color = color.copy(alpha = 0.7f))
    }
}

/** Мини-трафик ↓↑ справа в плитке локации (порт desktop stats-strip ВХОДЯЩИЙ/ИСХОДЯЩИЙ). */
@Composable
private fun TrafficMini(down: Long, up: Long) {
    val pack = NinetyState.pack
    val (dv, du) = Fmt.rate(down)
    val (uv, uu) = Fmt.rate(up)
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("↓ ", style = MonoStyle, color = pack.accentBright)
            Text(dv, style = MonoStyle, color = Ink.TextHi)
            Text(" $du", style = KickerStyle, color = Ink.TextLo)
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("↑ ", style = MonoStyle, color = Ink.TextMid)
            Text(uv, style = MonoStyle, color = Ink.TextHi)
            Text(" $uu", style = KickerStyle, color = Ink.TextLo)
        }
    }
}

/** Плитка активного профиля (порт .sub-card): имя, остаток дней, бар трафика, мета. */
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
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        profile.name, style = NinetyTypography.titleMedium, color = Ink.TextHi,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    profile.daysLeft()?.let {
                        Spacer(Modifier.width(10.dp))
                        Row {
                            Text("$it", style = KickerStyle, color = pack.accentBright)
                            Text(" ДН ОСТАЛОСЬ", style = KickerStyle, color = Ink.TextMid)
                        }
                    }
                }
                profile.usedFraction()?.let { frac ->
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(Ink.Line1)
                    ) {
                        Box(Modifier.fillMaxWidth(frac).height(2.dp).clip(RoundedCornerShape(1.dp)).background(pack.accent))
                    }
                }
                Spacer(Modifier.height(8.dp))
                val cnt = Store.nodeCount(profile.id)
                Text(
                    if (profile.isSub) "$cnt ${plural(cnt)}" else "Одиночный конфиг",
                    style = MonoStyle, color = Ink.TextLo,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(NinetyIcons.ChevronRight, contentDescription = null, tint = Ink.TextFaint, modifier = Modifier.size(16.dp))
    }
}

/** Плитка активной ноды (порт .loc-card): имя, протокол·безопасность, хост. */
@Composable
private fun LocationTile(onClick: () -> Unit) {
    val pack = NinetyState.pack
    val auto = Store.isAutoActive
    val node = Store.activeNode()
    val hasSelection = auto || node != null
    val snap = ClashMonitor.snapshot
    val connected = VpnController.state == ConnState.Connected

    // В авто-режиме показываем реальный выбранный узел (как desktop: auto.now).
    val effNode = if (auto) snap.autoNow?.let { tag -> Store.activeProfileNodes().firstOrNull { ConfigBuilder.tagOf(it) == tag } } else null

    Row(
        Modifier
            .fillMaxWidth()
            .premiumCard()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape)
                .background(if (hasSelection) pack.accent else Ink.Line3)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    auto -> "Авто"
                    node != null -> node.name.ifBlank { node.host }
                    else -> "Сервер не выбран"
                },
                style = NinetyTypography.titleMedium, color = Ink.TextHi,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val subtitle = when {
                auto -> effNode?.let { "→ ${it.name.ifBlank { it.host }}" } ?: "БЫСТРЕЙШИЙ УЗЕЛ"
                node != null -> "${node.proto.uppercase()} · ${if (node.security != "none") node.security.uppercase() else node.host}"
                else -> null
            }
            subtitle?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    it, style = KickerStyle,
                    color = if (auto && effNode != null) pack.accentBright else Ink.TextLo,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Трафик ↓↑ вместо пинга (пинг теперь под «Защищено» в hero). Только при туннеле.
        if (connected) {
            Spacer(Modifier.width(8.dp))
            TrafficMini(down = snap.down, up = snap.up)
        }
        Spacer(Modifier.width(8.dp))
        Icon(NinetyIcons.ChevronRight, contentDescription = null, tint = Ink.TextFaint, modifier = Modifier.size(14.dp))
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
