package pw.x4.ninety.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.ui.theme.ThemePack
import pw.x4.ninety.ui.theme.ThemePacks

@Composable
internal fun DesktopThemeGallery(selectedId: String, onSelect: (ThemePack) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemePacks.chunked(2).forEach { rowPacks ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowPacks.forEach { pack ->
                    DesktopThemeCard(
                        pack = pack,
                        selected = pack.id == selectedId,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(pack) },
                    )
                }
                if (rowPacks.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DesktopThemeCard(
    pack: ThemePack,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(NinetyRadius.md)
    Column(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (selected) pack.material.cardHoverTop else pack.material.cardTop,
                        pack.material.cardBottom,
                    ),
                ),
                shape,
            )
            .border(1.dp, if (selected) pack.accent else pack.material.border, shape)
            .clickable { onClick() }
            .padding(12.dp),
    ) {
        ThemeMiniature(pack, selected)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pack.label, style = NinetyTypography.titleMedium, color = pack.palette.textHi)
                Spacer(Modifier.height(3.dp))
                Text(pack.kicker, style = KickerStyle, color = if (selected) pack.material.secondary else pack.palette.textLo)
            }
            if (selected) {
                Text("ACTIVE", style = KickerStyle, color = pack.material.secondary)
            }
        }
    }
}

@Composable
private fun ThemeMiniature(pack: ThemePack, selected: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(94.dp)
            .clip(RoundedCornerShape(NinetyRadius.sm))
            .background(pack.palette.ink0)
            .border(1.dp, pack.material.border, RoundedCornerShape(NinetyRadius.sm)),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    listOf(pack.accentSoft.copy(alpha = 0.74f), Color.Transparent),
                    center = Offset(size.width * 0.72f, size.height * 1.05f),
                    radius = size.width * 0.66f,
                ),
            )
            if (pack.material.appSecondaryGlow != Color.Transparent) {
                drawRect(
                    brush = Brush.radialGradient(
                        listOf(pack.material.appSecondaryGlow, Color.Transparent),
                        center = Offset(size.width * 0.78f, size.height * 0.10f),
                        radius = size.width * 0.55f,
                    ),
                )
            }
            if (pack.material.grid) {
                val step = 16.dp.toPx()
                var x = 0f
                while (x <= size.width) {
                    drawLine(pack.palette.line1, Offset(x, 0f), Offset(x, size.height), 1f)
                    x += step
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(pack.palette.line1, Offset(0f, y), Offset(size.width, y), 1f)
                    y += step
                }
            }

            val sidebar = size.width * 0.26f
            drawRect(
                brush = Brush.verticalGradient(listOf(pack.material.sidebarTop, pack.material.sidebarBottom)),
                size = Size(sidebar, size.height),
            )
            repeat(3) { index ->
                val top = 18.dp.toPx() + index * 20.dp.toPx()
                drawRoundRect(
                    color = if (index == 0) pack.material.rowActive else pack.material.rowMiddle,
                    topLeft = Offset(6.dp.toPx(), top),
                    size = Size(sidebar - 12.dp.toPx(), 13.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                )
            }

            val center = Offset(size.width * 0.69f, size.height * 0.50f)
            val discRadius = 22.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(pack.material.discCenter, pack.material.discMiddle, pack.material.discEdge),
                    center = center,
                    radius = discRadius,
                ),
                radius = discRadius,
                center = center,
            )
            drawCircle(pack.material.border, discRadius + 1.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            drawCircle(pack.accent.copy(alpha = if (selected) 0.95f else 0.55f), discRadius + 10.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            drawArc(
                color = pack.material.secondary,
                startAngle = -76f,
                sweepAngle = 105f,
                useCenter = false,
                topLeft = Offset(center.x - discRadius - 15.dp.toPx(), center.y - discRadius - 15.dp.toPx()),
                size = Size((discRadius + 15.dp.toPx()) * 2f, (discRadius + 15.dp.toPx()) * 2f),
                style = Stroke(1.2.dp.toPx()),
            )
            drawLine(pack.accent, Offset(center.x - 4.dp.toPx(), center.y), Offset(center.x + 4.dp.toPx(), center.y), 1.dp.toPx())
            drawLine(pack.accent, Offset(center.x, center.y - 4.dp.toPx()), Offset(center.x, center.y + 4.dp.toPx()), 1.dp.toPx())
        }
    }
}
