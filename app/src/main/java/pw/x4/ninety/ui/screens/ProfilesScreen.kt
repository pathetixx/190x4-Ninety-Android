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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pw.x4.ninety.data.Importer
import pw.x4.ninety.data.Profile
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.components.IconTile
import pw.x4.ninety.ui.components.PillButton
import pw.x4.ninety.ui.components.ScreenHeader
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography

@Composable
fun ProfilesScreen() {
    val context = LocalContext.current
    val profiles = Store.profiles
    var busy by remember { mutableStateOf(false) }

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
                            onDone = { c, e -> busy = false; toast(context, e ?: "Обновлено узлов: $c") },
                        )
                    }
                }
                PillButton("Добавить") { addFromClipboard(context) }
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Подписки и одиночные конфиги. Активный профиль помечен точкой — используется при подключении.",
            style = NinetyTypography.bodyMedium, color = Ink.TextMid,
        )
        Spacer(Modifier.height(16.dp))

        if (profiles.isEmpty()) {
            Text(
                "Скопируйте ссылку подписки или конфиг (vless/vmess/trojan/ss/hysteria2/tuic)\nи нажмите «Добавить».",
                color = Ink.TextMid, style = NinetyTypography.bodyMedium,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(profiles, key = { it.id }) { p ->
                    ProfileCard(
                        profile = p,
                        active = p.id == Store.activeProfileId,
                        onSelect = { Store.setActiveProfile(p.id) },
                        onDelete = { Store.removeProfile(p.id); toast(context, "Профиль удалён") },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: Profile, active: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    val pack = NinetyState.pack
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) pack.accentSoft else Ink.Ink1, RoundedCornerShape(14.dp))
            .border(1.dp, if (active) pack.accent else Ink.Line2, RoundedCornerShape(14.dp))
            .clickable { onSelect() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(if (profile.isSub) NinetyIcons.Globe else NinetyIcons.Nodes, size = 44, accent = active)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.name, style = NinetyTypography.titleMedium, color = Ink.TextHi,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    AnnotatedString(if (profile.isSub) "ПОДПИСКА" else "КОНФИГ"),
                    style = KickerStyle.copy(fontSize = 9.sp),
                    color = pack.accentBright,
                    modifier = Modifier
                        .background(pack.accentSoft, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(5.dp))
            val sub = when {
                profile.isSub && profile.url.isNotBlank() -> profile.url
                profile.isSub -> "${Store.nodeCount(profile.id)} нод"
                else -> "Одиночный конфиг"
            }
            Text(sub, style = MonoStyle, color = Ink.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        // правая колонка: счётчик нод/дни + удаление
        Column(horizontalAlignment = Alignment.End) {
            if (profile.isSub) {
                Text("${Store.nodeCount(profile.id)}", style = NinetyTypography.titleMedium, color = Ink.TextHi)
                Text("НОД", style = KickerStyle, color = Ink.TextFaint)
            }
            profile.daysLeft()?.let {
                Spacer(Modifier.height(2.dp))
                Text("${it}д", style = MonoStyle, color = pack.accentBright)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(32.dp).clip(CircleShape).clickable { onDelete() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(NinetyIcons.Trash, contentDescription = "Удалить", tint = Ink.TextLo, modifier = Modifier.size(16.dp))
        }
    }
}

private fun toast(context: Context, msg: String) =
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

private fun addFromClipboard(context: Context) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
    Importer.importText(
        raw = text,
        onLoading = { Toast.makeText(context, "Загружаю…", Toast.LENGTH_SHORT).show() },
        onDone = { added, error ->
            toast(context, error ?: if (added > 0) "Добавлено: $added" else "Ничего не добавлено")
        },
    )
}
