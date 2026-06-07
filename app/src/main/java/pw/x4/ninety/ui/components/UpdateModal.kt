package pw.x4.ninety.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pw.x4.ninety.BuildConfig
import pw.x4.ninety.data.Prefs
import pw.x4.ninety.data.Updater
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography

/**
 * OTA-модалка (порт update-modal.js): текущая → новая версия, заметки релиза,
 * прогресс загрузки, «Позже» (скип версии) / «Обновить» (скачать+поставить).
 * Показывается из NinetyApp, когда Updater.Available.release != null.
 */
@Composable
fun UpdateModal(release: Updater.Release, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pack = NinetyState.pack
    var progress by remember { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }
    val installing = progress in 0..100

    Dialog(
        onDismissRequest = { if (!installing) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !installing,
            dismissOnClickOutside = !installing,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Ink.Ink1)
                .border(1.dp, Ink.Line2, RoundedCornerShape(20.dp))
                .padding(22.dp),
        ) {
            Text("ОБНОВЛЕНИЕ", style = KickerStyle, color = pack.accentBright)
            Spacer(Modifier.height(8.dp))
            Text("Доступна новая версия", style = NinetyTypography.headlineMedium, color = Ink.TextHi)

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                VersionChip(BuildConfig.VERSION_NAME, accent = false)
                Spacer(Modifier.width(10.dp))
                Text("→", style = NinetyTypography.titleMedium, color = Ink.TextLo)
                Spacer(Modifier.width(10.dp))
                VersionChip(release.version, accent = true)
            }

            val notes = release.notes.trim()
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.Ink0)
                        .border(1.dp, Ink.Line1, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(notes, style = MonoStyle, color = Ink.TextMid)
                }
            }

            AnimatedVisibility(visible = installing) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Ink.Line2),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0, 100) / 100f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(pack.accent),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Загрузка $progress%", style = KickerStyle, color = Ink.TextLo)
                }
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MonoStyle, color = Ink.Err, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!installing) {
                    PillButton("Позже", modifier = Modifier.weight(1f)) {
                        Prefs.get(context).skippedVersion = release.version
                        onDismiss()
                    }
                }
                PillButton(
                    if (installing) "Загрузка…" else "Обновить",
                    modifier = Modifier.weight(1f),
                    enabled = !installing,
                ) {
                    error = null
                    progress = 0
                    Updater.downloadAndInstall(
                        context, release,
                        onProgress = { progress = it },
                        onDone = { err ->
                            if (err != null) {
                                progress = -1
                                error = err
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                            // успех → системный установщик открыт; модалку оставляем,
                            // юзер вернётся и обновлённое приложение перезапустится.
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionChip(version: String, accent: Boolean) {
    val pack = NinetyState.pack
    Text(
        "v$version",
        style = MonoStyle,
        color = if (accent) pack.accentBright else Ink.TextMid,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (accent) pack.accentSoft else Ink.Ink2)
            .border(1.dp, if (accent) pack.accent else Ink.Line2, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
