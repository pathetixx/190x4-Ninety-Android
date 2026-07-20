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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import pw.x4.ninety.data.Fmt
import pw.x4.ninety.data.Profile
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.components.desktopCard
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.vpn.TunnelMode
import pw.x4.ninety.vpn.TunnelModes
import pw.x4.ninety.vpn.VpnController
import pw.x4.ninety.vpn.WarpRuntime

@Composable
internal fun DesktopSubscriptionToolbar(
    profile: Profile?,
    onOpenProfiles: () -> Unit,
) {
    val context = LocalContext.current
    var modeOpen by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

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
                            desktopQuotaText(it),
                            style = KickerStyle,
                            color = Ink.TextLo,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                DesktopQuotaBar(profile?.usedFraction())
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

        Box {
            DesktopToolbarButton(
                icon = NinetyIcons.Sliders,
                label = "Режим",
                selected = modeOpen,
            ) { modeOpen = !modeOpen }
            if (modeOpen) DesktopModePopover(onDismiss = { modeOpen = false })
        }
        DesktopToolbarButton(
            icon = NinetyIcons.Plus,
            label = "Добавить профиль",
        ) { showAdd = true }
    }

    if (showAdd) DesktopAddProfileDialog(context, onDismiss = { showAdd = false })
}

@Composable
private fun DesktopToolbarButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(NinetyRadius.md)
    Box(
        Modifier
            .width(46.dp)
            .heightIn(min = 66.dp)
            .clip(shape)
            .desktopCard(active = selected, shape = shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            label,
            tint = if (selected) NinetyState.pack.accentBright else Ink.TextMid,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun DesktopModePopover(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val current = TunnelModes.current()
    val active = VpnController.isActive
    val yOffset = with(density) { 74.dp.roundToPx() }
    val pack = NinetyState.pack

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, yOffset),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(NinetyRadius.md))
                .desktopCard(shape = RoundedCornerShape(NinetyRadius.md))
                .padding(16.dp),
        ) {
            Text("РЕЖИМ ПОДКЛЮЧЕНИЯ", style = KickerStyle, color = Ink.TextFaint)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                TunnelMode.entries.forEach { mode ->
                    val selected = mode == current
                    Box(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(NinetyRadius.xs))
                            .background(if (selected) pack.accentSoft else pack.material.cardBottom)
                            .border(
                                1.dp,
                                if (selected) pack.material.border else Ink.Line1,
                                RoundedCornerShape(NinetyRadius.xs),
                            )
                            .clickable(enabled = !active) {
                                TunnelModes.select(context, mode)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            desktopModeShortTitle(mode),
                            style = KickerStyle,
                            color = if (selected) pack.material.secondary else Ink.TextLo,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NinetyRadius.xs))
                    .background(pack.material.cardBottom)
                    .border(1.dp, Ink.Line1, RoundedCornerShape(NinetyRadius.xs))
                    .padding(11.dp),
            ) {
                Text(desktopModeTitle(current), style = NinetyTypography.titleSmall, color = Ink.TextHi)
                Spacer(Modifier.height(5.dp))
                Text(desktopModeHint(current), style = NinetyTypography.bodySmall, color = Ink.TextMid)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NinetyRadius.xs))
                    .background(pack.material.cardBottom)
                    .border(1.dp, Ink.Line1, RoundedCornerShape(NinetyRadius.xs))
                    .padding(horizontal = 11.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(NinetyIcons.Shield, null, tint = pack.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("WARP runtime", style = NinetyTypography.labelMedium, color = Ink.TextHi)
                    Text(
                        if (WarpRuntime.snapshot.registered) "Профиль зарегистрирован" else "Регистрация требуется для WARP",
                        style = KickerStyle,
                        color = Ink.TextFaint,
                    )
                }
                Box(
                    Modifier
                        .size(7.dp)
                        .background(
                            if (WarpRuntime.snapshot.registered) Ink.Ok else Ink.TextFaint,
                            CircleShape,
                        ),
                )
            }
            if (active) {
                Spacer(Modifier.height(10.dp))
                Text("ОСТАНОВИТЕ VPN, ЧТОБЫ СМЕНИТЬ РЕЖИМ", style = KickerStyle, color = Ink.Warn)
            }
        }
    }
}

@Composable
private fun DesktopQuotaBar(fraction: Float?) {
    Box(Modifier.fillMaxWidth().height(2.dp).background(Ink.Line1)) {
        Box(
            Modifier
                .fillMaxWidth(fraction ?: if (Store.activeProfile() == null) 0f else 1f)
                .height(2.dp)
                .background(NinetyState.pack.accent),
        )
    }
}

private fun desktopQuotaText(profile: Profile): String = if (profile.total > 0) {
    "${Fmt.bytes(profile.used)} / ${Fmt.bytes(profile.total)}"
} else {
    "UNLIMITED"
}

private fun desktopModeShortTitle(mode: TunnelMode): String = when (mode) {
    TunnelMode.PROXY -> "PROXY"
    TunnelMode.WARP_DIRECT -> "WARP"
    TunnelMode.WARP_CHAIN -> "CHAIN"
}

private fun desktopModeTitle(mode: TunnelMode): String = when (mode) {
    TunnelMode.PROXY -> "Прокси-узел"
    TunnelMode.WARP_DIRECT -> "WARP Direct"
    TunnelMode.WARP_CHAIN -> "Прокси → WARP"
}

private fun desktopModeHint(mode: TunnelMode): String = when (mode) {
    TunnelMode.PROXY -> "Трафик идёт через выбранную ноду или штатный Auto selector."
    TunnelMode.WARP_DIRECT -> "Прямой туннель Cloudflare WARP без профиля и proxy-ноды."
    TunnelMode.WARP_CHAIN -> "Сначала выбранная нода, затем второй защищённый hop через WARP."
}
