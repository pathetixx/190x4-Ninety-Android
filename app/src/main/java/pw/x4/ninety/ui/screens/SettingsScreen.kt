package pw.x4.ninety.ui.screens

import android.content.ClipData
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pw.x4.ninety.BuildConfig
import pw.x4.ninety.data.Prefs
import pw.x4.ninety.data.Updater
import pw.x4.ninety.ui.components.PillButton
import pw.x4.ninety.ui.components.ScreenHeader
import pw.x4.ninety.ui.components.SectionHeader
import pw.x4.ninety.ui.components.SurfaceCard
import pw.x4.ninety.ui.components.ToggleRow
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.ui.theme.ThemePacks

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = Prefs.get(context)
    val current = NinetyState.pack

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        ScreenHeader(kicker = "Settings", title = "Настройки")
        Spacer(Modifier.height(16.dp))

        SurfaceCard {
            SectionHeader(NinetyIcons.Settings, "Тема")
            Spacer(Modifier.height(12.dp))
            ThemePacks.forEach { pack ->
                val selected = pack.id == current.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            if (selected) pack.accentSoft else Ink.Ink3,
                            RoundedCornerShape(12.dp),
                        )
                        .border(
                            1.dp,
                            if (selected) pack.accent else Ink.Line2,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable {
                            NinetyState.pack = pack
                            prefs.themePack = pack.id
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .background(pack.accent, CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        pack.label,
                        color = if (selected) Ink.TextHi else Ink.TextMid,
                        style = NinetyTypography.titleMedium,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SurfaceCard { BehaviorSection(prefs) }
        Spacer(Modifier.height(16.dp))
        SurfaceCard { UpdateSection(context, prefs) }

        Spacer(Modifier.height(16.dp))

        SurfaceCard {
            SectionHeader(NinetyIcons.Shield, "О программе")
            Spacer(Modifier.height(10.dp))
            InfoRow("Версия", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            InfoRow("Ядро", "sing-box / libbox")
            InfoRow("Канал", "Early access")
        }

        Spacer(Modifier.height(16.dp))

        var refresh by remember { mutableIntStateOf(0) }
        val crash = remember(refresh) { pw.x4.ninety.data.Diag.lastCrash(context) }
        val stderr = remember(refresh) { pw.x4.ninety.data.Diag.boxStderr(context) }
        val runLog = remember(refresh) { pw.x4.ninety.data.Diag.boxRun(context) }
        SurfaceCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(NinetyIcons.Logs, "Логи")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "СКОПИРОВАТЬ",
                        style = pw.x4.ninety.ui.theme.MonoStyle,
                        color = current.accent,
                        modifier = Modifier.clickable { copyDiag(context) },
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "ОЧИСТИТЬ",
                        style = pw.x4.ninety.ui.theme.MonoStyle,
                        color = Ink.TextLo,
                        modifier = Modifier.clickable {
                            pw.x4.ninety.data.Diag.clear(context); refresh++
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (crash == null && stderr == null && runLog == null) {
                Text("Логов пока нет — подключись к узлу.", color = Ink.TextMid, style = NinetyTypography.bodyMedium)
            } else {
                crash?.let {
                    Text("Последний краш:", color = Ink.Err, style = NinetyTypography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = Ink.TextMid, style = pw.x4.ninety.ui.theme.MonoStyle)
                    Spacer(Modifier.height(10.dp))
                }
                stderr?.let {
                    Text("stderr ядра (паника):", color = Ink.TextLo, style = NinetyTypography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = Ink.TextMid, style = pw.x4.ninety.ui.theme.MonoStyle)
                    Spacer(Modifier.height(10.dp))
                }
                runLog?.let {
                    Text("лог ядра (хвост):", color = Ink.TextLo, style = NinetyTypography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = Ink.TextMid, style = pw.x4.ninety.ui.theme.MonoStyle)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Ink.TextMid, style = NinetyTypography.bodyMedium)
        Text(value, color = Ink.TextHi, style = NinetyTypography.bodyMedium)
    }
}

@Composable
private fun BehaviorSection(prefs: Prefs) {
    SectionHeader(NinetyIcons.Sliders, "Поведение")
    Spacer(Modifier.height(6.dp))
    var auto by remember { mutableStateOf(prefs.autoConnect) }
    ToggleRow("Автоподключение", auto, sub = "К последней ноде при запуске") {
        auto = it; prefs.autoConnect = it
    }
}

@Composable
private fun UpdateSection(context: Context, prefs: Prefs) {
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<Updater.Release?>(null) }
    var progress by remember { mutableIntStateOf(-1) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader(NinetyIcons.Refresh, "Обновление")
        PillButton(if (busy) "…" else "проверить", enabled = !busy) {
            status = null; found = null
            Updater.check(
                onLoading = { busy = true },
                onDone = { newer, err ->
                    busy = false
                    status = when {
                        err != null -> err
                        newer == null -> "Установлена последняя версия"
                        else -> { found = newer; "Доступна версия ${newer.version}" }
                    }
                },
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        status ?: "Версия ${BuildConfig.VERSION_NAME}",
        color = Ink.TextMid, style = NinetyTypography.bodyMedium,
    )
    found?.let { rel ->
        Spacer(Modifier.height(10.dp))
        PillButton(
            if (progress in 0..100) "загрузка $progress%" else "установить ${rel.version}",
            enabled = progress < 0,
        ) {
            Updater.downloadAndInstall(
                context, rel,
                onProgress = { progress = it },
                onDone = { err ->
                    progress = -1
                    if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                },
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    var autoChk by remember { mutableStateOf(prefs.autoUpdateCheck) }
    ToggleRow("Проверять при запуске", autoChk) { autoChk = it; prefs.autoUpdateCheck = it }
}

private fun copyDiag(context: Context) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("ninety-diag", pw.x4.ninety.data.Diag.fullReport(context)))
    Toast.makeText(context, "Диагностика скопирована", Toast.LENGTH_SHORT).show()
}
