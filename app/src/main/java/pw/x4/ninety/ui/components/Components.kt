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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography

/** Desktop `.sub-card` / `.loc-card` / `.prof-card` material. */
fun Modifier.premiumCard(shape: Shape = RoundedCornerShape(NinetyRadius.md)): Modifier =
    clip(shape).desktopCard(shape = shape)

/**
 * Top hairline gradient (CSS `::before`: transparent → highlight → transparent).
 */
fun Modifier.topHairline(color: Color = Color.White, alpha: Float = 0.08f): Modifier = drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.horizontalGradient(
            0.0f to Color.Transparent,
            0.3f to color.copy(alpha = alpha),
            0.7f to color.copy(alpha = alpha),
            1.0f to Color.Transparent,
        ),
        topLeft = Offset.Zero,
        size = Size(size.width, 1.dp.toPx()),
    )
}

@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(
        text = AnnotatedString(text.uppercase()),
        style = KickerStyle,
        color = if (accent) NinetyState.pack.material.secondary else Ink.TextLo,
        modifier = modifier,
    )
}

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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { acts() }
        }
    }
    sub?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = NinetyTypography.bodyMedium, color = Ink.TextMid)
    }
}

@Composable
fun IconTile(icon: ImageVector, size: Int = 38, accent: Boolean = false) {
    val pack = NinetyState.pack
    val shape = RoundedCornerShape(NinetyRadius.sm)
    Box(
        Modifier
            .size(size.dp)
            .background(if (accent) pack.accentSoft else pack.material.cardBottom, shape)
            .border(1.dp, if (accent) pack.material.border else Ink.Line1, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (accent) pack.material.secondary else Ink.TextMid,
            modifier = Modifier.size((size * 0.42f).dp),
        )
    }
}

@Composable
fun SectionHeader(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = NinetyState.pack.material.secondary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Kicker(label)
    }
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(NinetyRadius.lg)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .desktopCard(shape = shape)
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    val shape = RoundedCornerShape(NinetyRadius.xs)
    Text(
        AnnotatedString(text.uppercase()),
        style = MonoStyle,
        color = if (enabled) pack.material.secondary else Ink.TextLo,
        modifier = modifier
            .background(if (enabled) pack.accentSoft else Ink.Ink3, shape)
            .border(1.dp, if (enabled) pack.material.border else Ink.Line2, shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

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
            Text(
                "мс",
                style = KickerStyle,
                color = color.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, sub: String? = null, onToggle: (Boolean) -> Unit) {
    val pack = NinetyState.pack
    Row(
        Modifier
            .fillMaxWidth()
            .topHairline(alpha = if (pack.palette.isLight) 0.24f else 0.08f)
            .clickable { onToggle(!checked) }
            .padding(vertical = 9.dp),
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
                    .background(if (checked) pack.palette.ink0 else Ink.TextHi, CircleShape),
            )
        }
    }
}
