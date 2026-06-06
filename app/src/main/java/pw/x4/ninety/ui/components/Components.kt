package pw.x4.ninety.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography

/** Uppercase-кикер из tokens.css (.kicker). */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(
        text = AnnotatedString(text.uppercase()),
        style = KickerStyle,
        color = if (accent) NinetyState.pack.accent else Ink.TextLo,
        modifier = modifier,
    )
}

/** Карточка-поверхность (ink-2 + тонкая обводка line-2), радиус r-lg. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Ink.Ink2, RoundedCornerShape(18.dp))
            .border(1.dp, Ink.Line2, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

/** Пилюля-кнопка действия (стиль «ДОБАВИТЬ ИЗ БУФЕРА»: accent-обводка + soft-фон). */
@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val pack = NinetyState.pack
    Text(
        AnnotatedString(text.uppercase()),
        style = MonoStyle,
        color = if (enabled) pack.accent else Ink.TextLo,
        modifier = modifier
            .background(if (enabled) pack.accentSoft else Ink.Ink3, RoundedCornerShape(8.dp))
            .border(1.dp, if (enabled) pack.accent else Ink.Line2, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Строка-переключатель: лейбл (+подпись) слева, пилюля-тумблер справа. */
@Composable
fun ToggleRow(label: String, checked: Boolean, sub: String? = null, onToggle: (Boolean) -> Unit) {
    val pack = NinetyState.pack
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Ink.TextHi, style = NinetyTypography.titleMedium)
            sub?.let { Text(it, color = Ink.TextLo, style = MonoStyle) }
        }
        Box(
            Modifier
                .width(46.dp)
                .height(26.dp)
                .background(if (checked) pack.accent else Ink.Line2, RoundedCornerShape(13.dp))
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .background(Ink.TextHi, CircleShape)
            )
        }
    }
}
