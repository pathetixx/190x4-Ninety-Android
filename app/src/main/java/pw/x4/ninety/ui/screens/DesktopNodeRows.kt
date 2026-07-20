package pw.x4.ninety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pw.x4.ninety.data.Node
import pw.x4.ninety.ui.components.desktopCard
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.vpn.ProbePhase

/** One-to-one Compose equivalent of desktop `.prox` (28px flag + main + ping, 72px high). */
@Composable
internal fun DesktopFleetRow(
    auto: Boolean,
    node: Node?,
    selected: Boolean,
    effective: Boolean,
    effectiveName: String?,
    ping: Int?,
    phase: ProbePhase,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    val shape = RoundedCornerShape(NinetyRadius.md)
    val name = if (auto) "Авто" else node?.name?.ifBlank { node.host }.orEmpty()
    val subtitle = if (auto) {
        effectiveName?.let { "Сейчас → $it" }
            ?: if (phase == ProbePhase.Testing) "Выполняется замер" else "Штатный selector · urltest"
    } else {
        val protocol = node?.proto?.uppercase().orEmpty()
        val transport = node?.type?.uppercase().orEmpty()
        "${node?.host}:${node?.port} · $protocol · $transport"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(shape)
            .desktopCard(active = selected || effective, shape = shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopNodeFlag(auto = auto, node = node)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = NinetyTypography.titleSmall,
                color = if (auto) pack.accentBright else Ink.TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                style = MonoStyle.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
                color = if (auto && effectiveName != null) pack.material.secondary else Ink.TextLo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        DesktopNodePing(ping)
    }
}

@Composable
private fun DesktopNodeFlag(auto: Boolean, node: Node?) {
    val pack = NinetyState.pack
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .width(28.dp)
            .height(20.dp)
            .clip(shape)
            .background(if (auto) pack.accentSoft else Ink.Ink2)
            .border(1.dp, if (auto) pack.material.border else Ink.Line2, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (auto) {
            Icon(
                NinetyIcons.Nodes,
                contentDescription = null,
                tint = pack.accentBright,
                modifier = Modifier.size(14.dp),
            )
        } else {
            val glyph = desktopNodeFlag(node)
            Text(
                glyph,
                style = if (glyph.isRegionalFlag()) {
                    NinetyTypography.bodySmall.copy(fontSize = 13.sp, lineHeight = 15.sp)
                } else {
                    MonoStyle.copy(fontSize = 8.sp, lineHeight = 10.sp)
                },
                color = Ink.TextFaint,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DesktopNodePing(ms: Int?) {
    val dead = !desktopValidPing(ms)
    val color = when {
        dead -> Ink.TextFaint
        ms!! < 800 -> Ink.Ok
        ms < 1500 -> Ink.Warn
        else -> Ink.Err
    }
    Row(Modifier.width(56.dp), verticalAlignment = Alignment.Bottom) {
        Spacer(Modifier.weight(1f))
        Text(if (dead) "—" else ms.toString(), style = MonoStyle, color = color, maxLines = 1)
        if (!dead) {
            Spacer(Modifier.width(3.dp))
            Text("мс", style = KickerStyle.copy(fontSize = 9.sp), color = Ink.TextFaint)
        }
    }
}

private fun desktopNodeFlag(node: Node?): String {
    val name = node?.name.orEmpty()
    val points = name.codePoints().toArray()
    for (index in 0 until (points.size - 1).coerceAtLeast(0)) {
        if (points[index] in REGIONAL_INDICATORS && points[index + 1] in REGIONAL_INDICATORS) {
            return String(points, index, 2)
        }
    }
    return node?.host
        ?.substringAfterLast('.', "")
        ?.takeIf { it.length == 2 && it.all(Char::isLetter) }
        ?.uppercase()
        ?: "NN"
}

private fun String.isRegionalFlag(): Boolean {
    val points = codePoints().toArray()
    return points.size == 2 && points.all { it in REGIONAL_INDICATORS }
}

private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF

internal fun probePhaseLabel(phase: ProbePhase): String = when (phase) {
    ProbePhase.Offline -> "offline"
    ProbePhase.Connecting -> "connecting"
    ProbePhase.Testing -> "testing"
    ProbePhase.Partial -> "partial"
    ProbePhase.Ready -> "ready"
    ProbePhase.Error -> "error"
}
