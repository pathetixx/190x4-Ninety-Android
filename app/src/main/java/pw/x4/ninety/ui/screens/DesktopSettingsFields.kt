package pw.x4.ninety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pw.x4.ninety.ui.components.desktopCard
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography

@Composable
internal fun DesktopSettingsTitle(kicker: String, title: String, hint: String) {
    Text(kicker, style = KickerStyle, color = Ink.TextFaint)
    Spacer(Modifier.height(5.dp))
    Text(title, style = NinetyTypography.headlineMedium, color = Ink.TextHi)
    Spacer(Modifier.height(5.dp))
    Text(hint, style = NinetyTypography.bodyMedium, color = Ink.TextMid)
}

@Composable
internal fun DesktopSettingsCallout(kicker: String, text: String) {
    val pack = NinetyState.pack
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NinetyRadius.md))
            .desktopCard(active = true)
            .padding(13.dp),
    ) {
        Text(kicker, style = KickerStyle, color = pack.material.secondary)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MonoStyle, color = Ink.TextMid)
    }
}

@Composable
internal fun DesktopReloadHint() = DesktopSettingsCallout(
    "ENGINE · RELOAD",
    "Изменения ядра применятся при следующем подключении или явном reload.",
)

@Composable
internal fun DesktopTextSetting(label: String, hint: String, value: String, onValue: (String) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    DesktopSettingFieldHeader(label, hint)
    Spacer(Modifier.height(8.dp))
    BasicTextField(
        value = draft,
        onValueChange = {
            draft = it
            onValue(it)
        },
        singleLine = true,
        textStyle = MonoStyle.copy(color = Ink.TextHi),
        cursorBrush = SolidColor(NinetyState.pack.accent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NinetyRadius.xs))
            .background(NinetyState.pack.material.cardBottom)
            .border(1.dp, Ink.Line2, RoundedCornerShape(NinetyRadius.xs))
            .padding(horizontal = 11.dp, vertical = 10.dp),
    )
}

@Composable
internal fun DesktopNumberSetting(
    label: String,
    hint: String,
    value: Int,
    min: Int,
    max: Int,
    onValue: (Int) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value.toString()) }
    DesktopSettingFieldHeader(label, hint)
    Spacer(Modifier.height(8.dp))
    BasicTextField(
        value = draft,
        onValueChange = { raw ->
            draft = raw.filter(Char::isDigit)
            draft.toIntOrNull()?.coerceIn(min, max)?.let(onValue)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MonoStyle.copy(color = Ink.TextHi),
        cursorBrush = SolidColor(NinetyState.pack.accent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NinetyRadius.xs))
            .background(NinetyState.pack.material.cardBottom)
            .border(1.dp, Ink.Line2, RoundedCornerShape(NinetyRadius.xs))
            .padding(horizontal = 11.dp, vertical = 10.dp),
    )
}

@Composable
internal fun DesktopSelectSetting(
    label: String,
    hint: String,
    value: String,
    values: List<String>,
    labels: Map<String, String> = emptyMap(),
    onValue: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    DesktopSettingFieldHeader(label, hint)
    Spacer(Modifier.height(8.dp))
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NinetyRadius.xs))
                .background(NinetyState.pack.material.cardBottom)
                .border(1.dp, Ink.Line2, RoundedCornerShape(NinetyRadius.xs))
                .clickable { expanded = true }
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                labels[value] ?: value.uppercase(),
                style = MonoStyle,
                color = NinetyState.pack.material.secondary,
                modifier = Modifier.weight(1f),
            )
            Icon(NinetyIcons.ChevronRight, null, tint = Ink.TextFaint, modifier = Modifier.size(15.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labels[option] ?: option, style = MonoStyle) },
                    onClick = {
                        expanded = false
                        onValue(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun DesktopSettingFieldHeader(label: String, hint: String) {
    Text(label, style = NinetyTypography.titleMedium, color = Ink.TextHi)
    Spacer(Modifier.height(3.dp))
    Text(hint, style = MonoStyle, color = Ink.TextLo)
}

@Composable
internal fun DesktopFieldDivider() {
    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line1))
    Spacer(Modifier.height(14.dp))
}

@Composable
internal fun DesktopSettingsGap() = Spacer(Modifier.height(14.dp))

internal val DesktopRegionLabels = mapOf(
    "other" to "Другой",
    "ru" to "Россия",
    "cn" to "Китай",
    "ir" to "Иран",
    "tr" to "Турция",
    "by" to "Беларусь",
)

internal val DesktopIpv6Labels = mapOf(
    "disable" to "Отключить",
    "enable" to "Включить",
    "prefer" to "Предпочитать",
    "only" to "Только IPv6",
)

internal val DesktopTunLabels = mapOf(
    "mixed" to "Mixed",
    "gvisor" to "gVisor",
    "system" to "System",
)
