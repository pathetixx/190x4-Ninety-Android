package pw.x4.ninety.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pw.x4.ninety.data.Fmt
import pw.x4.ninety.data.Importer
import pw.x4.ninety.data.Profile
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.components.PillButton
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.layout.NinetyLayoutMetrics
import pw.x4.ninety.ui.layout.NinetyPage
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.vpn.NinetyVpnService
import pw.x4.ninety.vpn.TunnelModes
import pw.x4.ninety.vpn.VpnController

@Composable
fun ProfilesScreen(metrics: NinetyLayoutMetrics) {
    val context = LocalContext.current
    val profiles = Store.profiles
    var busy by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    NinetyPage(metrics) {
        Column(Modifier.fillMaxSize().padding(top = if (metrics.isCompact) 16.dp else 24.dp)) {
            ProfilesHeader(
                busy = busy,
                canRefresh = profiles.any { it.isSub && it.url.isNotBlank() },
                count = profiles.size,
                onRefresh = {
                    Importer.refreshAll(
                        onLoading = { busy = true },
                        onDone = { count, error ->
                            busy = false
                            toast(context, error ?: "Обновлено узлов: $count")
                        },
                    )
                },
                onAdd = { showAdd = true },
            )
            Spacer(Modifier.height(16.dp))
            ProfilesSummary(profiles)
            Spacer(Modifier.height(14.dp))

            if (profiles.isEmpty()) {
                EmptyState(Modifier.fillMaxSize()) { showAdd = true }
            } else {
                LazyVerticalGrid(
                    columns = if (metrics.isExpanded) GridCells.Fixed(1) else if (metrics.isCompact) GridCells.Fixed(1) else GridCells.Adaptive(330.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        if (metrics.isExpanded) {
                            DesktopProfileRow(
                                profile = profile,
                                active = profile.id == Store.activeProfileId,
                                onSelect = { selectProfile(context, profile.id) },
                                onDelete = { deleteProfile(context, profile.id) },
                            )
                        } else {
                            CompactProfileCard(
                                profile = profile,
                                active = profile.id == Store.activeProfileId,
                                onSelect = { selectProfile(context, profile.id) },
                                onDelete = { deleteProfile(context, profile.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) AddProfileDialog(context, onDismiss = { showAdd = false })
}

@Composable
private fun ProfilesHeader(
    busy: Boolean,
    canRefresh: Boolean,
    count: Int,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SUBSCRIPTIONS · $count", style = KickerStyle, color = Ink.TextFaint)
                Spacer(Modifier.height(5.dp))
                Text("Профили", style = NinetyTypography.headlineMedium, color = Ink.TextHi)
                Spacer(Modifier.height(5.dp))
                Text("Подписки и одиночные конфиги без скрытого изменения активного маршрута.", style = NinetyTypography.bodyMedium, color = Ink.TextMid)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canRefresh) {
                PillButton(if (busy) "Обновляю…" else "Обновить все", enabled = !busy, onClick = onRefresh)
            }
            PillButton("Добавить профиль", onClick = onAdd)
        }
    }
}

@Composable
private fun ProfilesSummary(profiles: List<Profile>) {
    val totalNodes = profiles.sumOf { Store.nodeCount(it.id) }
    val subscriptions = profiles.count(Profile::isSub)
    val active = Store.activeProfile()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink.Ink1)
            .border(1.dp, Ink.Line1, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryMetric("PROFILES", profiles.size.toString())
        SummaryMetric("NODES", totalNodes.toString())
        SummaryMetric("SUBS", subscriptions.toString())
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text("ACTIVE", style = KickerStyle, color = Ink.TextFaint)
            Text(active?.name ?: "—", style = MonoStyle, color = NinetyState.pack.accentBright, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column {
        Text(label, style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(3.dp))
        Text(value, style = NinetyTypography.titleMedium, color = Ink.TextHi)
    }
}

@Composable
private fun DesktopProfileRow(profile: Profile, active: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    val pack = NinetyState.pack
    Row(
        Modifier.fillMaxWidth().heightIn(min = 78.dp)
            .background(
                if (active) Brush.horizontalGradient(listOf(pack.accentSoft, Ink.Ink1, Ink.Ink1))
                else SolidColor(Ink.Ink1),
            )
            .border(1.dp, if (active) pack.accentSoft else Ink.Line1)
            .clickable { onSelect() }.padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(if (active) Ink.Ok else Ink.TextFaint, CircleShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1.45f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.name, style = NinetyTypography.titleMedium, color = Ink.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                StatusBadge(if (active) "ACTIVE" else if (profile.isSub) "SUB" else "SINGLE", active)
            }
            Spacer(Modifier.height(5.dp))
            Text(profileSource(profile), style = MonoStyle, color = Ink.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(18.dp))
        DesktopMetric("NODES", Store.nodeCount(profile.id).toString(), Modifier.width(74.dp))
        DesktopMetric("EXPIRES", profile.daysLeft()?.let { "$it d" } ?: "∞", Modifier.width(86.dp))
        DesktopMetric(
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

@Composable
private fun DesktopMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MonoStyle, color = Ink.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CompactProfileCard(profile: Profile, active: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    val pack = NinetyState.pack
    val shape = RoundedCornerShape(15.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (active) Brush.verticalGradient(listOf(pack.accentSoft, Ink.Ink1, Ink.Ink1)) else SolidColor(Ink.Ink1))
            .border(1.dp, if (active) pack.accentSoft else Ink.Line1, shape)
            .clickable { onSelect() }.padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (active) pack.accentSoft else Ink.Ink2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (profile.isSub) NinetyIcons.Globe else NinetyIcons.Nodes, null, tint = if (active) pack.accentBright else Ink.TextMid, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = NinetyTypography.titleMedium, color = Ink.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(profileSource(profile), style = MonoStyle, color = Ink.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(36.dp).clickable { onDelete() }, contentAlignment = Alignment.Center) {
                Icon(NinetyIcons.Trash, "Удалить", tint = Ink.TextLo, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryMetric("NODES", Store.nodeCount(profile.id).toString())
            SummaryMetric("EXPIRES", profile.daysLeft()?.let { "$it d" } ?: "∞")
            SummaryMetric("STATE", if (active) "ACTIVE" else "READY")
        }
    }
}

@Composable
private fun StatusBadge(text: String, active: Boolean) {
    Text(
        text,
        style = KickerStyle,
        color = if (active) NinetyState.pack.accentBright else Ink.TextMid,
        modifier = Modifier.background(if (active) NinetyState.pack.accentSoft else Ink.Ink3, RoundedCornerShape(4.dp))
            .border(1.dp, if (active) NinetyState.pack.accent else Ink.Line2, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Column(modifier.padding(top = 38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(Ink.Ink2).border(1.dp, Ink.Line2), contentAlignment = Alignment.Center) {
            Icon(NinetyIcons.Profiles, null, tint = Ink.TextFaint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("SUBSCRIPTIONS · EMPTY", style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(8.dp))
        Text("Нет профилей", style = NinetyTypography.titleLarge, color = Ink.TextHi)
        Spacer(Modifier.height(8.dp))
        Text(
            "Добавьте подписку или одиночный VLESS, VMess, Trojan, Shadowsocks, Hysteria2 или TUIC.",
            style = NinetyTypography.bodyMedium,
            color = Ink.TextMid,
            modifier = Modifier.widthIn(max = 480.dp),
        )
        Spacer(Modifier.height(18.dp))
        PillButton("Добавить профиль", onClick = onAdd)
    }
}

@Composable
private fun AddProfileDialog(context: Context, onDismiss: () -> Unit) {
    val pack = NinetyState.pack
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun import(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) {
            error = "Пусто — вставьте ссылку подписки или конфиг"
            return
        }
        error = null
        Importer.importText(
            raw = value,
            onLoading = { busy = true },
            onDone = { added, importError ->
                busy = false
                if (importError != null) {
                    error = importError
                } else {
                    toast(context, if (added > 0) "Добавлено: $added" else "Ничего не добавлено")
                    onDismiss()
                }
            },
        )
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().widthIn(max = 560.dp)
                .clip(RoundedCornerShape(18.dp)).background(Ink.Ink1)
                .border(1.dp, Ink.Line2, RoundedCornerShape(18.dp)).padding(22.dp),
        ) {
            Text("ADD · PROFILE", style = KickerStyle, color = pack.accentBright)
            Spacer(Modifier.height(6.dp))
            Text("Добавить профиль", style = NinetyTypography.titleLarge, color = Ink.TextHi)
            Spacer(Modifier.height(5.dp))
            Text("Ссылка подписки, одиночный конфиг или список конфигов — каждый с новой строки.", style = NinetyTypography.bodyMedium, color = Ink.TextMid)
            Spacer(Modifier.height(17.dp))
            Box(
                Modifier.fillMaxWidth().background(pack.accentSoft).border(1.dp, pack.accent)
                    .clickable(enabled = !busy) { import(readClipboard(context)) }.padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (busy) "ЗАГРУЖАЮ…" else "ВСТАВИТЬ ИЗ БУФЕРА", style = MonoStyle, color = pack.accentBright)
            }
            Spacer(Modifier.height(14.dp))
            Text("ИЛИ ВСТАВЬТЕ ВРУЧНУЮ", style = KickerStyle, color = Ink.TextFaint)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(min = 96.dp).background(Ink.Ink2)
                    .border(1.dp, Ink.Line2).padding(12.dp),
            ) {
                if (text.isEmpty()) Text("vless://… или https://…", style = MonoStyle, color = Ink.TextFaint)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MonoStyle.copy(color = Ink.TextHi),
                    cursorBrush = SolidColor(pack.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MonoStyle, color = Ink.Err)
            }
            Spacer(Modifier.height(17.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton("ОТМЕНА", primary = false, enabled = !busy, modifier = Modifier.weight(1f), onClick = onDismiss)
                DialogButton("ДОБАВИТЬ", primary = true, enabled = !busy, modifier = Modifier.weight(1f)) { import(text) }
            }
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    Box(
        modifier.background(if (primary) pack.accentSoft else Ink.Ink2)
            .border(1.dp, if (primary) pack.accent else Ink.Line2)
            .clickable(enabled = enabled) { onClick() }.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MonoStyle, color = if (primary) pack.accentBright else Ink.TextMid)
    }
}

private fun selectProfile(context: Context, profileId: String) {
    Store.setActiveProfile(profileId)
    if (VpnController.isActive && TunnelModes.current().requiresProxySelection) NinetyVpnService.reload(context)
}

private fun deleteProfile(context: Context, profileId: String) {
    Store.removeProfile(profileId)
    toast(context, "Профиль удалён")
}

private fun profileSource(profile: Profile): String = when {
    profile.isSub && profile.url.isNotBlank() -> profile.url
    profile.isSub -> "${Store.nodeCount(profile.id)} нод"
    else -> singleHost(profile)
}

private fun singleHost(profile: Profile): String {
    val node = Store.nodesOf(profile.id).firstOrNull() ?: return "Одиночный конфиг"
    return "${node.host}:${node.port}"
}

private fun readClipboard(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
}

private fun toast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
