package pw.x4.ninety.ui.components

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography

@Composable
internal fun DesktopConfirmDialog(
    kicker: String,
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(NinetyRadius.lg))
                .desktopCard(shape = RoundedCornerShape(NinetyRadius.lg))
                .padding(22.dp),
        ) {
            Text(kicker.uppercase(), style = KickerStyle, color = Ink.TextFaint)
            Spacer(Modifier.height(6.dp))
            Text(title, style = NinetyTypography.titleLarge, color = Ink.TextHi)
            Spacer(Modifier.height(9.dp))
            Text(message, style = NinetyTypography.bodyMedium, color = Ink.TextMid)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DesktopConfirmButton(
                    text = "ОТМЕНА",
                    primary = false,
                    destructive = false,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                DesktopConfirmButton(
                    text = confirmLabel.uppercase(),
                    primary = true,
                    destructive = destructive,
                    modifier = Modifier.weight(1f),
                ) {
                    onConfirm()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun DesktopConfirmButton(
    text: String,
    primary: Boolean,
    destructive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    val shape = RoundedCornerShape(NinetyRadius.xs)
    val accent = if (destructive) Ink.Err else pack.accent
    Box(
        modifier
            .height(40.dp)
            .clip(shape)
            .background(
                when {
                    destructive -> Ink.Err.copy(alpha = 0.12f)
                    primary -> pack.accentSoft
                    else -> pack.material.cardBottom
                },
            )
            .border(1.dp, if (primary) accent.copy(alpha = 0.55f) else Ink.Line2, shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MonoStyle,
            color = when {
                destructive -> Ink.Err
                primary -> pack.material.secondary
                else -> Ink.TextMid
            },
        )
    }
}
