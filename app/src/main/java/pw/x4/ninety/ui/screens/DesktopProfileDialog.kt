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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pw.x4.ninety.data.Importer
import pw.x4.ninety.ui.components.desktopCard
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography

@Composable
internal fun DesktopAddProfileDialog(context: Context, onDismiss: () -> Unit) {
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
                    desktopProfileToast(context, if (added > 0) "Добавлено: $added" else "Ничего не добавлено")
                    onDismiss()
                }
            },
        )
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(NinetyRadius.lg))
                .desktopCard(shape = RoundedCornerShape(NinetyRadius.lg))
                .padding(22.dp),
        ) {
            Text("ADD · PROFILE", style = KickerStyle, color = pack.material.secondary)
            Spacer(Modifier.height(6.dp))
            Text("Добавить профиль", style = NinetyTypography.titleLarge, color = Ink.TextHi)
            Spacer(Modifier.height(5.dp))
            Text(
                "Ссылка подписки, одиночный конфиг или список конфигов — каждый с новой строки.",
                style = NinetyTypography.bodyMedium,
                color = Ink.TextMid,
            )
            Spacer(Modifier.height(17.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(pack.accentSoft, RoundedCornerShape(NinetyRadius.xs))
                    .border(1.dp, pack.material.border, RoundedCornerShape(NinetyRadius.xs))
                    .clickable(enabled = !busy) { import(readDesktopClipboard(context)) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (busy) "ЗАГРУЖАЮ…" else "ВСТАВИТЬ ИЗ БУФЕРА",
                    style = MonoStyle,
                    color = pack.material.secondary,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("ИЛИ ВСТАВЬТЕ ВРУЧНУЮ", style = KickerStyle, color = Ink.TextFaint)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp)
                    .background(pack.material.cardBottom, RoundedCornerShape(NinetyRadius.xs))
                    .border(1.dp, Ink.Line2, RoundedCornerShape(NinetyRadius.xs))
                    .padding(12.dp),
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
                DesktopDialogButton("ОТМЕНА", primary = false, enabled = !busy, modifier = Modifier.weight(1f), onClick = onDismiss)
                DesktopDialogButton("ДОБАВИТЬ", primary = true, enabled = !busy, modifier = Modifier.weight(1f)) { import(text) }
            }
        }
    }
}

@Composable
private fun DesktopDialogButton(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    Box(
        modifier
            .height(44.dp)
            .background(if (primary) pack.accentSoft else pack.material.cardBottom, RoundedCornerShape(NinetyRadius.xs))
            .border(1.dp, if (primary) pack.material.border else Ink.Line2, RoundedCornerShape(NinetyRadius.xs))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MonoStyle, color = if (primary) pack.material.secondary else Ink.TextMid)
    }
}

private fun readDesktopClipboard(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
}

internal fun desktopProfileToast(context: Context, message: String) =
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
