package pw.x4.ninety.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography

/** Desktop `update-modal__card` port with the same 520px frame and action density. */
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
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            Modifier
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(NinetyRadius.lg))
                .desktopCard(shape = RoundedCornerShape(NinetyRadius.lg))
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Text("UPDATE · AVAILABLE", style = KickerStyle, color = Ink.TextFaint)
            Spacer(Modifier.height(6.dp))
            Text("Доступна новая версия", style = NinetyTypography.titleLarge, color = Ink.TextHi)
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
                        .clip(RoundedCornerShape(NinetyRadius.sm))
                        .background(pack.material.cardBottom)
                        .border(1.dp, Ink.Line1, RoundedCornerShape(NinetyRadius.sm))
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(notes, style = MonoStyle, color = Ink.TextMid)
                }
            }

            AnimatedVisibility(visible = installing) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("DOWNLOADING", style = KickerStyle, color = Ink.TextFaint)
                        Spacer(Modifier.weight(1f))
                        Text("$progress%", style = MonoStyle, color = pack.accentBright)
                    }
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Ink.Line2),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0, 100) / 100f)
                                .height(4.dp)
                                .background(pack.accent),
                        )
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MonoStyle, color = Ink.Err, textAlign = TextAlign.Start)
            }

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!installing) {
                    DesktopUpdateButton("Позже", primary = false) {
                        Prefs.get(context).skippedVersion = release.version
                        onDismiss()
                    }
                    Spacer(Modifier.width(10.dp))
                }
                DesktopUpdateButton(
                    text = if (installing) "Загрузка…" else "Обновить",
                    primary = true,
                    enabled = !installing,
                ) {
                    error = null
                    progress = 0
                    Updater.downloadAndInstall(
                        context = context,
                        rel = release,
                        onProgress = { progress = it },
                        onDone = { err ->
                            if (err != null) {
                                progress = -1
                                error = err
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopUpdateButton(
    text: String,
    primary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    val shape = RoundedCornerShape(NinetyRadius.sm)
    Box(
        Modifier
            .width(112.dp)
            .height(36.dp)
            .clip(shape)
            .background(
                when {
                    !enabled -> Ink.Ink3
                    primary -> pack.accent
                    else -> Ink.Overlay1
                },
            )
            .border(
                1.dp,
                when {
                    !enabled -> Ink.Line1
                    primary -> pack.accentBright
                    else -> Ink.Line2
                },
                shape,
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = NinetyTypography.labelMedium,
            color = when {
                !enabled -> Ink.TextFaint
                primary -> Ink.Ink0
                else -> Ink.TextMid
            },
        )
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
            .clip(RoundedCornerShape(NinetyRadius.xs))
            .background(if (accent) pack.accentSoft else pack.material.cardBottom)
            .border(1.dp, if (accent) pack.accent else Ink.Line2, RoundedCornerShape(NinetyRadius.xs))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
