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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pw.x4.ninety.data.Node
import pw.x4.ninety.ui.components.PingPill
import pw.x4.ninety.ui.components.desktopCard
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.vpn.ProbePhase

@Composable
internal fun DesktopFleetRow(
    auto: Boolean,
    node: Node?,
    selected: Boolean,
    effective: Boolean,
    effectiveName: String?,
    ping: Int?,
    phase: ProbePhase,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    val shape = RoundedCornerShape(if (compact) NinetyRadius.md else NinetyRadius.sm)
    val name = if (auto) "Авто" else node?.name?.ifBlank { node.host }.orEmpty()
    val subtitle = if (auto) {
        effectiveName?.let { "Сейчас → $it" }
            ?: if (phase == ProbePhase.Testing) "Выполняется замер" else "Ожидание native urltest"
    } else {
        "${node?.host}:${node?.port}"
    }

    if (compact) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .desktopCard(active = selected || effective, shape = shape)
                .clickable { onClick() }
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(
                            if (auto || selected) pack.accentSoft else pack.material.cardBottom,
                            RoundedCornerShape(NinetyRadius.sm),
                        )
                        .border(
                            1.dp,
                            if (auto || selected) pack.material.border else Ink.Line1,
                            RoundedCornerShape(NinetyRadius.sm),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (auto) NinetyIcons.Refresh else NinetyIcons.Nodes,
                        null,
                        tint = if (auto || selected) pack.material.secondary else Ink.TextMid,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name,
                            color = Ink.TextHi,
                            style = NinetyTypography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (effective) {
                            Spacer(Modifier.width(7.dp))
                            FleetBadge("IN USE", true)
                        }
                    }
                    Spacer(Modifier.heightIn(min = 4.dp))
                    Text(
                        subtitle,
                        color = if (auto && effectiveName != null) pack.material.secondary else Ink.TextLo,
                        style = MonoStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(9.dp))
                PingPill(ping)
            }
            Spacer(Modifier.heightIn(min = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (auto) {
                    FleetMeta("POLICY", "URLTEST")
                    FleetMeta("STATE", probePhaseLabel(phase).uppercase())
                    FleetMeta("SELECTION", if (selected) "AUTO" else "READY")
                } else {
                    FleetMeta("PROTOCOL", node?.proto?.uppercase().orEmpty())
                    FleetMeta("SECURITY", node?.security?.uppercase()?.takeIf { it != "NONE" } ?: "PLAIN")
                    FleetMeta("TRANSPORT", node?.type?.uppercase().orEmpty())
                }
            }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (auto) 72.dp else 70.dp)
                .clip(shape)
                .desktopCard(active = selected || effective, shape = shape)
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(8.dp).background(
                    if (effective) pack.material.status else if (selected) pack.accent else Ink.TextFaint,
                    CircleShape,
                ),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1.45f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = NinetyTypography.titleMedium,
                        color = Ink.TextHi,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (auto) {
                        Spacer(Modifier.width(8.dp))
                        FleetBadge("NATIVE URLTEST", selected)
                    } else if (effective) {
                        Spacer(Modifier.width(8.dp))
                        FleetBadge("IN USE", true)
                    }
                }
                Spacer(Modifier.heightIn(min = 4.dp))
                Text(
                    subtitle,
                    style = MonoStyle,
                    color = if (auto && effectiveName != null) pack.material.secondary else Ink.TextLo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (auto) {
                FleetMeta("POLICY", "URLTEST", Modifier.width(92.dp))
                FleetMeta("STATE", probePhaseLabel(phase).uppercase(), Modifier.width(104.dp))
            } else {
                FleetMeta("PROTOCOL", node?.proto?.uppercase().orEmpty(), Modifier.width(100.dp))
                FleetMeta("SECURITY", node?.security?.uppercase()?.takeIf { it != "NONE" } ?: "PLAIN", Modifier.width(104.dp))
                FleetMeta("TRANSPORT", node?.type?.uppercase().orEmpty(), Modifier.width(100.dp))
            }
            Box(Modifier.width(86.dp), contentAlignment = Alignment.CenterEnd) { PingPill(ping) }
        }
    }
}

@Composable
private fun FleetMeta(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.heightIn(min = 4.dp))
        Text(value, style = MonoStyle, color = Ink.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FleetBadge(text: String, active: Boolean) {
    val pack = NinetyState.pack
    Text(
        text,
        style = KickerStyle,
        color = if (active) pack.material.secondary else Ink.TextMid,
        modifier = Modifier
            .background(if (active) pack.accentSoft else Ink.Ink3, RoundedCornerShape(4.dp))
            .border(1.dp, if (active) pack.material.border else Ink.Line2, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

internal fun probePhaseLabel(phase: ProbePhase): String = when (phase) {
    ProbePhase.Offline -> "offline"
    ProbePhase.Connecting -> "connecting"
    ProbePhase.Testing -> "testing"
    ProbePhase.Partial -> "partial"
    ProbePhase.Ready -> "ready"
    ProbePhase.Error -> "error"
}
