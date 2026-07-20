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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pw.x4.ninety.data.Fmt
import pw.x4.ninety.data.Profile
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.components.desktopCard
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography

@Composable
internal fun DesktopProfileEntry(
    profile: Profile,
    active: Boolean,
    compact: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val pack = NinetyState.pack
    val shape = RoundedCornerShape(if (compact) NinetyRadius.md else NinetyRadius.sm)
    if (compact) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .desktopCard(active = active, shape = shape)
                .clickable { onSelect() }
                .padding(15.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(if (active) pack.accentSoft else pack.material.cardBottom, RoundedCornerShape(NinetyRadius.sm))
                        .border(1.dp, if (active) pack.material.border else Ink.Line1, RoundedCornerShape(NinetyRadius.sm)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (profile.isSub) NinetyIcons.Globe else NinetyIcons.Nodes,
                        null,
                        tint = if (active) pack.material.secondary else Ink.TextMid,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            profile.name,
                            style = NinetyTypography.titleMedium,
                            color = Ink.TextHi,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        ProfileBadge(if (active) "ACTIVE" else if (profile.isSub) "SUB" else "SINGLE", active)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        desktopProfileSource(profile),
                        style = MonoStyle,
                        color = Ink.TextLo,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(Modifier.size(36.dp).clickable { onDelete() }, contentAlignment = Alignment.Center) {
                    Icon(NinetyIcons.Trash, "Удалить", tint = Ink.TextLo, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            ProfileQuota(profile)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ProfileMetric("NODES", Store.nodeCount(profile.id).toString())
                ProfileMetric("EXPIRES", profile.daysLeft()?.let { "$it d" } ?: "∞")
                ProfileMetric("UPDATED", Fmt.relTime(profile.updatedAt))
            }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 82.dp)
                .clip(shape)
                .desktopCard(active = active, shape = shape)
                .clickable { onSelect() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(if (active) pack.material.status else Ink.TextFaint, CircleShape))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1.4f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.name,
                        style = NinetyTypography.titleMedium,
                        color = Ink.TextHi,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    ProfileBadge(if (active) "ACTIVE" else if (profile.isSub) "SUB" else "SINGLE", active)
                }
                Spacer(Modifier.height(5.dp))
                Text(desktopProfileSource(profile), style = MonoStyle, color = Ink.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                ProfileQuota(profile)
            }
            Spacer(Modifier.width(18.dp))
            ProfileMetric("NODES", Store.nodeCount(profile.id).toString(), Modifier.width(72.dp))
            ProfileMetric("EXPIRES", profile.daysLeft()?.let { "$it d" } ?: "∞", Modifier.width(82.dp))
            ProfileMetric(
                if (profile.total > 0) "TRAFFIC" else "UPDATED",
                if (profile.total > 0) Fmt.bytes(profile.used) else Fmt.relTime(profile.updatedAt),
                Modifier.width(112.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(38.dp).clickable { onDelete() }, contentAlignment = Alignment.Center) {
                Icon(NinetyIcons.Trash, "Удалить", tint = Ink.TextLo, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun ProfileQuota(profile: Profile) {
    val fraction = profile.usedFraction()
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(
                if (profile.total > 0) "${Fmt.bytes(profile.used)} / ${Fmt.bytes(profile.total)}" else "UNLIMITED",
                style = KickerStyle,
                color = Ink.TextFaint,
                modifier = Modifier.weight(1f),
            )
            fraction?.let { Text("${(it * 100).toInt()}%", style = KickerStyle, color = NinetyState.pack.material.secondary) }
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Ink.Line1)) {
            Box(
                Modifier
                    .fillMaxWidth(fraction ?: 1f)
                    .height(2.dp)
                    .background(if (fraction == null) Ink.Line3 else NinetyState.pack.accent),
            )
        }
    }
}

@Composable
private fun ProfileMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MonoStyle, color = Ink.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProfileBadge(text: String, active: Boolean) {
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

internal fun desktopProfileSource(profile: Profile): String = when {
    profile.isSub && profile.url.isNotBlank() -> profile.url
    profile.isSub -> "${Store.nodeCount(profile.id)} нод"
    else -> Store.nodesOf(profile.id).firstOrNull()?.let { "${it.host}:${it.port}" } ?: "Одиночный конфиг"
}
