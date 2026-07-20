package pw.x4.ninety.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pw.x4.ninety.data.Importer
import pw.x4.ninety.ui.components.desktopCard
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography

private enum class AddProfilePage { Options, Manual, Loading }

/** Desktop `.add-modal`: option tiles → manual editor/loading state. */
@Composable
internal fun DesktopAddProfileDialog(context: Context, onDismiss: () -> Unit) {
    val pack = NinetyState.pack
    var page by remember { mutableStateOf(AddProfilePage.Options) }
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun import(raw: String, requestedName: String? = null) {
        val value = raw.trim()
        if (value.isEmpty()) {
            error = "Пусто — вставьте ссылку подписки или конфиг"
            page = AddProfilePage.Manual
            return
        }
        error = null
        page = AddProfilePage.Loading
        Importer.importText(
            raw = value,
            name = requestedName,
            onLoading = { page = AddProfilePage.Loading },
            onDone = { added, importError ->
                if (importError != null) {
                    error = importError
                    page = AddProfilePage.Manual
                } else {
                    desktopProfileToast(context, if (added > 0) "Добавлено: $added" else "Ничего не добавлено")
                    onDismiss()
                }
            },
        )
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Не удалось прочитать файл")
            }.onSuccess { import(it, name.takeIf { value -> value.isNotBlank() }) }
                .onFailure {
                    error = it.message ?: "Не удалось прочитать файл"
                    page = AddProfilePage.Options
                }
        }
    }

    Dialog(
        onDismissRequest = { if (page != AddProfilePage.Loading) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = page != AddProfilePage.Loading,
            dismissOnClickOutside = page != AddProfilePage.Loading,
            usePlatformDefaultWidth = false,
        ),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ADD · PROFILE", style = KickerStyle, color = pack.material.secondary)
                    Spacer(Modifier.height(5.dp))
                    Text("Добавить профиль", style = NinetyTypography.titleLarge, color = Ink.TextHi)
                }
                if (page != AddProfilePage.Loading) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(NinetyRadius.xs))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", style = NinetyTypography.titleLarge, color = Ink.TextLo)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            when (page) {
                AddProfilePage.Options -> DesktopAddOptions(
                    error = error,
                    onClipboard = { import(readDesktopClipboard(context)) },
                    onManual = { error = null; page = AddProfilePage.Manual },
                    onFile = { filePicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) },
                )
                AddProfilePage.Manual -> DesktopManualProfilePage(
                    name = name,
                    text = text,
                    error = error,
                    onNameChange = { name = it },
                    onTextChange = { text = it },
                    onBack = { error = null; page = AddProfilePage.Options },
                    onSubmit = { import(text, name.takeIf { value -> value.isNotBlank() }) },
                )
                AddProfilePage.Loading -> DesktopProfileLoadingPage()
            }
        }
    }
}

@Composable
private fun DesktopAddOptions(
    error: String?,
    onClipboard: () -> Unit,
    onManual: () -> Unit,
    onFile: () -> Unit,
) {
    Text(
        "Вставьте подписку по URL, proxy-ссылку, список конфигов или выберите локальный файл.",
        style = NinetyTypography.bodyMedium,
        color = Ink.TextMid,
    )
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DesktopAddTile(
            icon = NinetyIcons.Profiles,
            title = "Из буфера",
            subtitle = "URL и proxy-ссылки",
            modifier = Modifier.weight(1f),
            onClick = onClipboard,
        )
        DesktopAddTile(
            icon = NinetyIcons.Plus,
            title = "Вручную",
            subtitle = "Название и содержимое",
            modifier = Modifier.weight(1f),
            onClick = onManual,
        )
        DesktopAddTile(
            icon = NinetyIcons.File,
            title = "Файл",
            subtitle = "TXT, TOML, конфиг",
            modifier = Modifier.weight(1f),
            onClick = onFile,
        )
    }
    error?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, style = MonoStyle, color = Ink.Err)
    }
}

@Composable
private fun DesktopAddTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    Column(
        modifier
            .height(132.dp)
            .clip(RoundedCornerShape(NinetyRadius.md))
            .background(pack.material.cardBottom)
            .border(1.dp, Ink.Line2, RoundedCornerShape(NinetyRadius.md))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(NinetyRadius.sm))
                .background(pack.accentSoft)
                .border(1.dp, pack.material.border, RoundedCornerShape(NinetyRadius.sm)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = pack.material.secondary, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = NinetyTypography.titleSmall, color = Ink.TextHi, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = KickerStyle, color = Ink.TextFaint, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DesktopManualProfilePage(
    name: String,
    text: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    DesktopProfileField(
        label = "НАЗВАНИЕ · НЕОБЯЗАТЕЛЬНО",
        value = name,
        placeholder = "Моя подписка",
        singleLine = true,
        minHeight = 44,
        onValueChange = onNameChange,
    )
    Spacer(Modifier.height(13.dp))
    DesktopProfileField(
        label = "URL ИЛИ КОНФИГ",
        value = text,
        placeholder = "https://…  ·  vless://…  ·  список ссылок",
        singleLine = false,
        minHeight = 116,
        onValueChange = onTextChange,
    )
    error?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, style = MonoStyle, color = Ink.Err)
    }
    Spacer(Modifier.height(17.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DesktopDialogButton("НАЗАД", primary = false, modifier = Modifier.weight(1f), onClick = onBack)
        DesktopDialogButton("ДОБАВИТЬ", primary = true, modifier = Modifier.weight(1f), onClick = onSubmit)
    }
}

@Composable
private fun DesktopProfileField(
    label: String,
    value: String,
    placeholder: String,
    singleLine: Boolean,
    minHeight: Int,
    onValueChange: (String) -> Unit,
) {
    val pack = NinetyState.pack
    Text(label, style = KickerStyle, color = Ink.TextFaint)
    Spacer(Modifier.height(7.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp)
            .background(pack.material.cardBottom, RoundedCornerShape(NinetyRadius.xs))
            .border(1.dp, Ink.Line2, RoundedCornerShape(NinetyRadius.xs))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, style = MonoStyle, color = Ink.TextFaint)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = MonoStyle.copy(color = Ink.TextHi),
            cursorBrush = SolidColor(pack.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DesktopProfileLoadingPage() {
    val rotation by rememberInfiniteTransition(label = "add-profile-loading").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "add-profile-ring",
    )
    Column(
        Modifier.fillMaxWidth().height(220.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .rotate(rotation)
                .border(2.dp, NinetyState.pack.accent, CircleShape),
        )
        Spacer(Modifier.height(18.dp))
        Text("ЗАГРУЖАЮ ПРОФИЛЬ", style = KickerStyle, color = NinetyState.pack.material.secondary)
        Spacer(Modifier.height(7.dp))
        Text("Проверяю подписку и разбираю ноды…", style = NinetyTypography.bodyMedium, color = Ink.TextMid)
    }
}

@Composable
private fun DesktopDialogButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(NinetyRadius.xs))
            .background(if (primary) pack.accentSoft else pack.material.cardBottom)
            .border(1.dp, if (primary) pack.material.border else Ink.Line2, RoundedCornerShape(NinetyRadius.xs))
            .clickable { onClick() },
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
