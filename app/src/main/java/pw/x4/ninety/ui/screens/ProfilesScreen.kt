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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import pw.x4.ninety.data.Fmt
import pw.x4.ninety.data.Importer
import pw.x4.ninety.data.Profile
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.components.IconTile
import pw.x4.ninety.ui.components.PillButton
import pw.x4.ninety.ui.components.ScreenHeader
import pw.x4.ninety.ui.components.topHairline
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.vpn.NinetyVpnService
import pw.x4.ninety.vpn.VpnController

@Composable
fun ProfilesScreen() {
    val context = LocalContext.current
    val profiles = Store.profiles
    var busy by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        ScreenHeader(
            kicker = "Subscriptions",
            title = "Профили",
            actions = {
                if (profiles.any { it.isSub && it.url.isNotBlank() }) {
                    PillButton(if (busy) "…" else "Обновить", enabled = !busy) {
                        Importer.refreshAll(
                            onLoading = { busy = true },
                            onDone = { count, error ->
                                busy = false
                                toast(context, error ?: "Обновлено узлов: $count")
                            },
                        )
                    }
                }
                PillButton("Добавить") { showAdd = true }
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Подписки и одиночные конфиги. Активный профиль помечен точкой — используется при подключении.",
            style = NinetyTypography.bodyMedium,
            color = Ink.TextMid,
        )
        Spacer(Modifier.height(16.dp))

        if (profiles.isEmpty()) {
            EmptyState { showAdd = true }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        active = profile.id == Store.activeProfileId,
                        onSelect = {
                            Store.setActiveProfile(profile.id)
                            if (VpnController.isActive) NinetyVpnService.reload(context)
                        },
                        onDelete = {
                            Store.removeProfile(profile.id)
                            toast(context, "Профиль удалён")
                        },
                    )
                }
            }
        }
    }

    if (showAdd) AddModal(context, onDismiss = { showAdd = false })
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SUBSCRIPTIONS · EMPTY", style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(10.dp))
        Text("Нет профилей", style = NinetyTypography.titleLarge, color = Ink.TextHi)
        Spacer(Modifier.height(8.dp))
        Text(
            "Скопируйте ссылку подписки или конфиг (vless/vmess/trojan/ss/hysteria2/tuic) и нажмите «Добавить».",
            style = NinetyTypography.bodyMedium,
            color = Ink.TextMid,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
        PillButton("Добавить") { onAdd() }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    active: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val pack = NinetyState.pack
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (active) {
                    Brush.verticalGradient(
                        0f to pack.accentSoft,
                        0.4f to Ink.Ink1,
                        1f to Ink.Ink1,
                    )
                } else {
                    SolidColor(Ink.Ink1)
                },
                RoundedCornerShape(16.dp),
            )
            .border(1.dp, if (active) pack.accentSoft else Ink.Line2, RoundedCornerShape(16.dp))
            .topHairline(
                color = if (active) pack.accent else Color.White,
                alpha = if (active) 0.5f else 0.08f,
            )
            .clickable { onSelect() }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                if (profile.isSub) NinetyIcons.Globe else NinetyIcons.Nodes,
                size = 42,
                accent = active,
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.name,
                        style = NinetyTypography.titleMedium,
                        color = Ink.TextHi,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Badge(
                        if (active) "АКТИВНЫЙ" else if (profile.isSub) "ПОДПИСКА" else "КОНФИГ",
                        active,
                    )
                }
                Spacer(Modifier.height(4.dp))
                val subtitle = when {
                    profile.isSub && profile.url.isNotBlank() -> profile.url
                    profile.isSub -> "${Store.nodeCount(profile.id)} нод"
                    else -> singleHost(profile)
                }
                Text(
                    subtitle,
                    style = MonoStyle,
                    color = Ink.TextLo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable { onDelete() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    NinetyIcons.Trash,
                    "Удалить",
                    tint = Ink.TextLo,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        profile.usedFraction()?.let { fraction ->
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Ink.Line1),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(pack.accent),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            if (profile.isSub) {
                StatCell("${Store.nodeCount(profile.id)}", "УЗЛОВ")
                StatCell(profile.daysLeft()?.let { "$it" } ?: "∞", "ИСТЕКАЕТ")
                if (profile.total > 0) {
                    StatCell(Fmt.bytes(profile.used), "ТРАФИК")
                } else {
                    StatCell(Fmt.relTime(profile.updatedAt), "ОБНОВЛЕНО")
                }
            } else {
                val node = Store.nodesOf(profile.id).firstOrNull()
                StatCell((node?.proto ?: "vless").uppercase(), "ПРОТОКОЛ")
                StatCell(
                    (node?.security?.takeIf { it != "none" } ?: "tcp").uppercase(),
                    "БЕЗОПАСНОСТЬ",
                )
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column {
        Text(
            value,
            style = MonoStyle.copy(fontSize = 13.sp),
            color = Ink.TextHi,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(label, style = KickerStyle.copy(fontSize = 9.sp), color = Ink.TextFaint)
    }
}

@Composable
private fun Badge(text: String, active: Boolean) {
    val pack = NinetyState.pack
    Text(
        AnnotatedString(text),
        style = KickerStyle.copy(fontSize = 9.sp),
        color = if (active) pack.accentBright else Ink.TextMid,
        modifier = Modifier
            .background(if (active) pack.accentSoft else Ink.Ink3, RoundedCornerShape(4.dp))
            .border(1.dp, if (active) pack.accent else Ink.Line2, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun AddModal(context: Context, onDismiss: () -> Unit) {
    val pack = NinetyState.pack
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun doImport(raw: String) {
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
                    Toast.makeText(
                        context,
                        if (added > 0) "Добавлено: $added" else "Ничего не добавлено",
                        Toast.LENGTH_LONG,
                    ).show()
                    onDismiss()
                }
            },
        )
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Ink.Ink1, RoundedCornerShape(20.dp))
                .border(1.dp, Ink.Line2, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text("ADD · PROFILE", style = KickerStyle, color = pack.accentBright)
            Spacer(Modifier.height(6.dp))
            Text("Добавить профиль", style = NinetyTypography.titleLarge, color = Ink.TextHi)
            Spacer(Modifier.height(4.dp))
            Text(
                "Ссылка подписки (http/https) или конфиг vless/vmess/trojan/ss/hysteria2/tuic. Можно вставить список — каждый конфиг с новой строки.",
                style = NinetyTypography.bodyMedium,
                color = Ink.TextMid,
            )
            Spacer(Modifier.height(16.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(pack.accentSoft)
                    .border(1.dp, pack.accent, RoundedCornerShape(12.dp))
                    .clickable(enabled = !busy) { doImport(readClipboard(context)) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (busy) "ЗАГРУЖАЮ…" else "ВСТАВИТЬ ИЗ БУФЕРА",
                    style = MonoStyle,
                    color = pack.accentBright,
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("или вставьте вручную", style = KickerStyle, color = Ink.TextFaint)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp)
                    .background(Ink.Ink2, RoundedCornerShape(10.dp))
                    .border(1.dp, Ink.Line2, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                if (text.isEmpty()) {
                    Text("vless://… или https://…", style = MonoStyle, color = Ink.TextFaint)
                }
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

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ModalBtn(
                    "ОТМЕНА",
                    primary = false,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { onDismiss() }
                ModalBtn(
                    "ДОБАВИТЬ",
                    primary = true,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { doImport(text) }
            }
        }
    }
}

@Composable
private fun ModalBtn(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) pack.accentSoft else Ink.Ink2)
            .border(1.dp, if (primary) pack.accent else Ink.Line2, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MonoStyle,
            color = if (primary) pack.accentBright else Ink.TextMid,
        )
    }
}

private fun singleHost(profile: Profile): String {
    val node = Store.nodesOf(profile.id).firstOrNull() ?: return "Одиночный конфиг"
    return "${node.host}:${node.port}"
}

private fun readClipboard(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
        ?.trim()
        .orEmpty()
}

private fun toast(context: Context, message: String) =
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
