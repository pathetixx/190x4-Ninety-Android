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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

/** Compose port of desktop `.prof-card`: 44px icon, main, stats and overflow menu. */
@Composable
internal fun DesktopProfileEntry(
    profile: Profile,
    active: Boolean,
    compact: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val pack = NinetyState.pack
    val shape = RoundedCornerShape(NinetyRadius.md)

    if (compact) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .desktopCard(active = active, shape = shape)
                .clickable { onSelect() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileIcon(profile, active)
                Spacer(Modifier.width(14.dp))
                ProfileMain(profile, active, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                ProfileMenu(active, onSelect, onDelete)
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                profileStats(profile).forEach { stat ->
                    ProfileMetric(stat.label, stat.value)
                }
            }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .clip(shape)
                .desktopCard(active = active, shape = shape)
                .clickable { onSelect() }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileIcon(profile, active)
            Spacer(Modifier.width(16.dp))
            ProfileMain(profile, active, Modifier.weight(1f))
            Spacer(Modifier.width(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Top) {
                profileStats(profile).forEach { stat ->
                    ProfileMetric(stat.label, stat.value, Modifier.width(stat.width.dp), alignEnd = true)
                }
            }
            Spacer(Modifier.width(8.dp))
            ProfileMenu(active, onSelect, onDelete)
        }
    }
}

@Composable
private fun ProfileIcon(profile: Profile, active: Boolean) {
    val pack = NinetyState.pack
    val shape = RoundedCornerShape(NinetyRadius.sm)
    Box(
        Modifier
            .size(44.dp)
            .background(if (active) pack.accentSoft else Ink.Ink2, shape)
            .border(1.dp, if (active) ColorTransparent else Ink.Line1, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (profile.isSub) NinetyIcons.Globe else NinetyIcons.File,
            contentDescription = null,
            tint = if (active) pack.accentBright else Ink.TextMid,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ProfileMain(profile: Profile, active: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
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
                if (active) {
                    Spacer(Modifier.width(10.dp))
                    ProfileBadge("ACTIVE")
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                desktopProfileSource(profile),
                style = MonoStyle,
                color = Ink.TextLo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProfileMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(value, style = MonoStyle, color = Ink.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(label, style = KickerStyle, color = Ink.TextFaint, maxLines = 1)
    }
}

@Composable
private fun ProfileBadge(text: String) {
    val pack = NinetyState.pack
    Text(
        text,
        style = KickerStyle,
        color = pack.accentBright,
        modifier = Modifier
            .background(pack.accentSoft, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun ProfileMenu(active: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val pack = NinetyState.pack
    Box {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(NinetyRadius.xs))
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(NinetyIcons.More, "Меню профиля", tint = Ink.TextFaint, modifier = Modifier.size(17.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(pack.material.cardTop).border(1.dp, pack.material.border),
        ) {
            if (!active) {
                DropdownMenuItem(
                    text = { Text("Сделать активным", style = NinetyTypography.bodyMedium, color = Ink.TextHi) },
                    onClick = {
                        expanded = false
                        onSelect()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Удалить", style = NinetyTypography.bodyMedium, color = Ink.Err) },
                leadingIcon = { Icon(NinetyIcons.Trash, null, tint = Ink.Err, modifier = Modifier.size(16.dp)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

private data class ProfileStat(val label: String, val value: String, val width: Int)

private fun profileStats(profile: Profile): List<ProfileStat> {
    if (profile.isSub) {
        val traffic = if (profile.total > 0) {
            "${Fmt.bytes(profile.used)} / ${Fmt.bytes(profile.total)}"
        } else {
            "${Fmt.bytes(profile.used)} · ∞"
        }
        return listOf(
            ProfileStat("NODES", Store.nodeCount(profile.id).toString(), 52),
            ProfileStat("TRAFFIC", traffic, 126),
            ProfileStat("EXPIRES", profile.daysLeft()?.let { "$it d" } ?: "—", 58),
            ProfileStat("UPDATED", Fmt.relTime(profile.updatedAt), 82),
        )
    }

    val node = Store.nodesOf(profile.id).firstOrNull()
    return listOf(
        ProfileStat("PROTO", node?.proto?.uppercase() ?: "—", 62),
        ProfileStat("TLS", node?.security?.uppercase()?.takeIf { it != "NONE" } ?: "PLAIN", 72),
        ProfileStat("TRAFFIC", Fmt.bytes(profile.used), 88),
    )
}

internal fun desktopProfileSource(profile: Profile): String = when {
    profile.isSub && profile.url.isNotBlank() -> profile.url
    profile.isSub -> "${Store.nodeCount(profile.id)} нод"
    else -> Store.nodesOf(profile.id).firstOrNull()?.let { "${it.host}:${it.port}" } ?: "Одиночный конфиг"
}

private val ColorTransparent = androidx.compose.ui.graphics.Color.Transparent
