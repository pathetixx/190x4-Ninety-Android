package pw.x4.ninety.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import pw.x4.ninety.core.model.WarpRegistrationSanitizer
import pw.x4.ninety.data.Options
import pw.x4.ninety.ui.components.DesktopConfirmDialog
import pw.x4.ninety.ui.components.PillButton
import pw.x4.ninety.ui.components.SurfaceCard
import pw.x4.ninety.ui.components.ToggleRow
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.vpn.WarpRuntime

@Composable
fun WarpSettingsPane() {
    val context = LocalContext.current
    val status = WarpRuntime.snapshot
    val options = Options.data
    var license by remember { mutableStateOf("") }
    var licenseError by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(status.registered) {
        if (status.registered) {
            license = ""
            licenseError = null
        }
    }

    SurfaceCard {
        Text("CLOUDFLARE · WARP", style = KickerStyle, color = NinetyState.pack.accentBright)
        Spacer(Modifier.height(7.dp))
        Text(
            when {
                status.warpPlus -> "WARP+ зарегистрирован"
                status.registered -> "Бесплатный WARP зарегистрирован"
                else -> "Устройство не зарегистрировано"
            },
            style = NinetyTypography.titleLarge,
            color = Ink.TextHi,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "WireGuard-ключ создаётся локально. Приватный ключ, access token и WARP+ license сохраняются отдельно и шифруются Android Keystore.",
            style = NinetyTypography.bodyMedium,
            color = Ink.TextMid,
        )
        if (status.registered) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                WarpMetric("ACCOUNT", status.accountType.ifBlank { if (status.warpPlus) "plus" else "free" })
                WarpMetric("PLAN", if (status.warpPlus) "WARP+" else "WARP")
                WarpMetric("STATE", if (options.warpEnabled) "ACTIVE" else "READY")
            }
        }
        status.error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MonoStyle, color = Ink.Err)
        }
    }

    Spacer(Modifier.height(14.dp))
    if (!status.registered) {
        SurfaceCard {
            Text("РЕГИСТРАЦИЯ", style = KickerStyle, color = Ink.TextFaint)
            Spacer(Modifier.height(9.dp))
            WarpTextField(
                value = license,
                placeholder = "WARP+ license — необязательно",
                secret = true,
                onValueChange = {
                    license = it.filterNot(Char::isWhitespace)
                    licenseError = null
                },
            )
            licenseError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MonoStyle, color = Ink.Err)
            }
            Spacer(Modifier.height(12.dp))
            PillButton(
                text = if (status.busy) "Регистрирую…" else if (license.isBlank()) "Создать бесплатный WARP" else "Активировать WARP+",
                enabled = !status.busy,
            ) {
                val clean = license.trim()
                if (clean.isNotEmpty() && clean.length != WarpRegistrationSanitizer.LICENSE_LENGTH) {
                    licenseError = "WARP+ ключ должен содержать ровно ${WarpRegistrationSanitizer.LICENSE_LENGTH} символов"
                } else {
                    WarpRuntime.register(clean.ifBlank { null })
                }
            }
        }
        return
    }

    SurfaceCard {
        ToggleRow("Использовать WARP", options.warpEnabled, "Защищённый outbound становится финальным маршрутом") {
            Options.update(context) { current -> current.copy(warpEnabled = it) }
            WarpRuntime.applySettings()
        }
        Spacer(Modifier.height(8.dp))
        Text("РЕЖИМ", style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WarpChoice("Direct", options.warpMode == "direct") {
                Options.update(context) { current -> current.copy(warpMode = "direct") }
            }
            WarpChoice("Chain", options.warpMode == "chain") {
                Options.update(context) { current -> current.copy(warpMode = "chain") }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            if (options.warpMode == "chain") "Сначала активная прокси-нода, затем Cloudflare WARP." else "Трафик выходит напрямую через Cloudflare WARP.",
            style = MonoStyle,
            color = Ink.TextLo,
        )
    }

    Spacer(Modifier.height(14.dp))
    SurfaceCard {
        Text("ENDPOINT · WIREGUARD", style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(9.dp))
        WarpTextField(
            value = options.warpEndpoint,
            placeholder = "engage.cloudflareclient.com:2408",
            onValueChange = { value -> Options.update(context) { it.copy(warpEndpoint = value) } },
        )
        Spacer(Modifier.height(12.dp))
        WarpNumberRow("MTU", "576–1500", options.warpMtu, 576, 1500) { value ->
            Options.update(context) { it.copy(warpMtu = value) }
        }
    }

    Spacer(Modifier.height(14.dp))
    SurfaceCard {
        Text("AMNEZIAWG · NOISE", style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("off" to "Off", "default" to "Default", "aggressive" to "Aggressive", "custom" to "Custom")
                .forEach { (value, label) ->
                    WarpChoice(label, options.warpNoisePreset == value, Modifier.weight(1f)) {
                        Options.update(context) { it.copy(warpNoisePreset = value) }
                    }
                }
        }
        if (options.warpNoisePreset == "custom") {
            Spacer(Modifier.height(12.dp))
            WarpRangeRow("COUNT", options.warpCountFrom, options.warpCountTo, 1, 64) { from, to ->
                Options.update(context) { it.copy(warpCountFrom = from, warpCountTo = to) }
            }
            WarpRangeRow("SIZE", options.warpSizeFrom, options.warpSizeTo, 1, 1500) { from, to ->
                Options.update(context) { it.copy(warpSizeFrom = from, warpSizeTo = to) }
            }
            WarpRangeRow("DELAY", options.warpDelayFrom, options.warpDelayTo, 0, 5000) { from, to ->
                Options.update(context) { it.copy(warpDelayFrom = from, warpDelayTo = to) }
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        PillButton("Применить") { WarpRuntime.applySettings() }
        PillButton(if (status.busy) "Сбрасываю…" else "Сбросить регистрацию", enabled = !status.busy) {
            confirmReset = true
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "Endpoint scanner и автоматический re-scan desktop-версии не включены в этот Android-этап. Можно указать найденный endpoint вручную.",
        style = MonoStyle,
        color = Ink.TextLo,
    )

    if (confirmReset) {
        DesktopConfirmDialog(
            kicker = "WARP · Reset",
            title = "Сбросить регистрацию WARP?",
            message = "Локальные ключи, токен устройства и WARP+ license будут удалены. Для повторного подключения потребуется новая регистрация.",
            confirmLabel = "Сбросить",
            destructive = true,
            onDismiss = { confirmReset = false },
            onConfirm = { WarpRuntime.reset() },
        )
    }
}

@Composable
private fun WarpMetric(label: String, value: String) {
    Column {
        Text(label, style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(3.dp))
        Text(value.uppercase(), style = MonoStyle, color = Ink.TextHi)
    }
}

@Composable
private fun WarpChoice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pack = NinetyState.pack
    Box(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) pack.accentSoft else Ink.Ink3)
            .border(1.dp, if (selected) pack.accent else Ink.Line2, RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = MonoStyle, color = if (selected) pack.accentBright else Ink.TextMid)
    }
}

@Composable
private fun WarpTextField(
    value: String,
    placeholder: String,
    secret: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Ink.Ink3, RoundedCornerShape(9.dp))
            .border(1.dp, Ink.Line2, RoundedCornerShape(9.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, style = MonoStyle, color = Ink.TextFaint)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = MonoStyle.copy(color = Ink.TextHi),
            cursorBrush = SolidColor(NinetyState.pack.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WarpNumberRow(label: String, hint: String, value: Int, min: Int, max: Int, onValue: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = NinetyTypography.titleMedium, color = Ink.TextHi)
            Text(hint, style = MonoStyle, color = Ink.TextLo)
        }
        WarpNumberField(value, min, max, onValue)
    }
}

@Composable
private fun WarpRangeRow(label: String, from: Int, to: Int, min: Int, max: Int, onValue: (Int, Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = KickerStyle, color = Ink.TextFaint, modifier = Modifier.weight(1f))
        WarpNumberField(from, min, max) { onValue(it, to) }
        Text("—", style = MonoStyle, color = Ink.TextLo, modifier = Modifier.padding(horizontal = 7.dp))
        WarpNumberField(to, min, max) { onValue(from, it) }
    }
}

@Composable
private fun WarpNumberField(value: Int, min: Int, max: Int, onValue: (Int) -> Unit) {
    var draft by remember(value) { mutableStateOf(value.toString()) }
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
            .width(82.dp)
            .background(Ink.Ink3, RoundedCornerShape(8.dp))
            .border(1.dp, Ink.Line2, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp),
    )
}
