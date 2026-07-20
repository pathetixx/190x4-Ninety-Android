package pw.x4.ninety.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import pw.x4.ninety.BuildConfig
import pw.x4.ninety.data.Diag
import pw.x4.ninety.data.Updater
import pw.x4.ninety.ui.components.PillButton
import pw.x4.ninety.ui.components.SurfaceCard
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography

@Composable
internal fun DesktopLogsSettings() {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }
    val crash = remember(refreshKey) { Diag.lastCrash(context) }
    val stderr = remember(refreshKey) { Diag.boxStderr(context) }
    val run = remember(refreshKey) { Diag.boxRun(context) }
    val logcat = remember(refreshKey) { Diag.logcat(context) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { PillButton("Обновить") { refreshKey++ } }
            Box(Modifier.weight(1f)) {
                PillButton("Скопировать") {
                    copyDesktopText(context, Diag.fullReport(context))
                    desktopSettingsToast(context, "Диагностика скопирована")
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { PillButton("Поделиться") { shareDesktopDiagnostics(context) } }
            Box(Modifier.weight(1f)) {
                PillButton("Очистить") {
                    Diag.clear(context)
                    refreshKey++
                    desktopSettingsToast(context, "Логи очищены")
                }
            }
        }
    }
    DesktopSettingsGap()
    DesktopDiagnosticCard("ПОСЛЕДНИЙ КРАШ", crash)
    DesktopSettingsGap()
    DesktopDiagnosticCard("STDERR ЯДРА", stderr)
    DesktopSettingsGap()
    DesktopDiagnosticCard("ЛОГ SING-BOX", run)
    DesktopSettingsGap()
    DesktopDiagnosticCard("LOGCAT SNAPSHOT", logcat)
}

@Composable
private fun DesktopDiagnosticCard(title: String, value: String?) {
    SurfaceCard {
        Text(title, style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(8.dp))
        Text(
            value ?: "Нет данных",
            style = MonoStyle,
            color = if (value == null) Ink.TextLo else Ink.TextMid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun DesktopAboutSettings() {
    val context = LocalContext.current
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(60.dp).background(NinetyState.pack.accentSoft, RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("九", style = NinetyTypography.headlineMedium, color = NinetyState.pack.material.secondary)
            }
            Spacer(Modifier.size(14.dp))
            Column {
                Text("Ninety Android", style = NinetyTypography.titleLarge, color = Ink.TextHi)
                Text("v${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}", style = MonoStyle, color = Ink.TextLo)
            }
        }
        DesktopSettingsGap()
        Text(
            "Нативный Android-клиент 190x4 на VpnService и libbox с визуальной системой desktop Ninety.",
            style = NinetyTypography.bodyMedium,
            color = Ink.TextMid,
        )
    }
    DesktopSettingsGap()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(if (checking) "Проверяю…" else "Проверить обновление", enabled = !checking) {
            Updater.check(
                onLoading = { checking = true },
                onDone = { release, error ->
                    checking = false
                    result = error ?: release?.let { "Доступна версия ${it.version}" } ?: "Установлена актуальная версия"
                    if (release != null) Updater.Available.release = release
                },
            )
        }
        PillButton("GitHub") { openDesktopUri(context, "https://github.com/pathetixx/190x4-Ninety-Android") }
    }
    result?.let {
        DesktopSettingsGap()
        Text(it, style = MonoStyle, color = Ink.TextMid)
    }
}

private fun copyDesktopText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Ninety diagnostics", text))
}

private fun shareDesktopDiagnostics(context: Context) {
    runCatching {
        val file = Diag.writeReportFile(context)
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Поделиться диагностикой",
            ),
        )
    }.onFailure { desktopSettingsToast(context, it.message ?: "Не удалось поделиться") }
}

private fun openDesktopUri(context: Context, value: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value))) }
        .onFailure { desktopSettingsToast(context, "Не удалось открыть ссылку") }
}

private fun desktopSettingsToast(context: Context, message: String) =
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
