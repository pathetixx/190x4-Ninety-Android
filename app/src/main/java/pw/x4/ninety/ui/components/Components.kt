package pw.x4.ninety.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

/** Заголовок экрана: kicker (mono, разреженный) + крупный title + опц. подзаголовок. */
@Composable
fun ScreenHeader(
    kicker: String,
    title: String,
    sub: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(AnnotatedString(kicker.uppercase()), style = KickerStyle, color = Ink.TextFaint)
            Spacer(Modifier.height(5.dp))
            Text(title, style = NinetyTypography.headlineMedium, color = Ink.TextHi)
        }
        actions?.let { acts ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { acts() }
        }
    }
    sub?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = NinetyTypography.bodyMedium, color = Ink.TextMid)
    }
}

/** Квадратная плитка-иконка (как .sub-card__icon / .prof-card__icon). */
@Composable
fun IconTile(icon: ImageVector, size: Int = 38, accent: Boolean = false) {
    val pack = NinetyState.pack
    Box(
        Modifier
            .size(size.dp)
            .background(if (accent) pack.accentSoft else Ink.Ink2, RoundedCornerShape(10.dp))
            .border(1.dp, if (accent) Color.Transparent else Ink.Line1, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (accent) pack.accentBright else Ink.TextMid, modifier = Modifier.size((size * 0.42f).dp))
    }
}

/** Заголовок секции в Настройках: иконка + kicker. */
@Composable
fun SectionHeader(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = NinetyState.pack.accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Kicker(label)
    }
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

/**
 * Пилюля пинга ноды (грейд по задержке, как .prox__ping на desktop). null/0/>=65000
 * = недоступна → «—». Цвета: <800 зелёный, <1500 янтарь, иначе красный.
 */
@Composable
fun PingPill(ms: Int?, modifier: Modifier = Modifier) {
    val dead = ms == null || ms <= 0 || ms >= 65000
    val color = when {
        dead -> Ink.TextLo
        ms!! < 800 -> Ink.Ok
        ms < 1500 -> Ink.Warn
        else -> Ink.Err
    }
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(if (dead) "—" else "$ms", style = NinetyTypography.titleMedium, color = color)
        if (!dead) {
            Spacer(Modifier.width(2.dp))
            Text("мс", style = KickerStyle, color = color.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 2.dp))
        }
    }
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
