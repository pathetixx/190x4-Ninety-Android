package pw.x4.ninety.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyState
import kotlin.random.Random

/**
 * Desktop `.app::before`/`::after` port: matte base, accent bloom, optional
 * premium secondary glow, titanium grid, kintsugi seams and stable film grain.
 */
@Composable
fun DesktopBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val pack = NinetyState.pack
    Box(modifier.fillMaxSize().background(pack.palette.ink0)) {
        Canvas(Modifier.fillMaxSize()) {
            val baseRadius = size.maxDimension * 0.78f
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        pack.accentSoft.copy(alpha = if (pack.palette.isLight) 0.24f else 0.48f),
                        pack.accentSoft.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.56f, size.height * 1.08f),
                    radius = baseRadius,
                ),
            )
            if (pack.material.appSecondaryGlow != Color.Transparent) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(pack.material.appSecondaryGlow, Color.Transparent),
                        center = Offset(size.width * 0.78f, size.height * 0.16f),
                        radius = size.maxDimension * 0.56f,
                    ),
                )
            }
            if (pack.material.grid) {
                val step = 48.dp.toPx()
                var x = 0f
                while (x <= size.width) {
                    drawLine(Ink.Line1, Offset(x, 0f), Offset(x, size.height), 1f)
                    x += step
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(Ink.Line1, Offset(0f, y), Offset(size.width, y), 1f)
                    y += step
                }
            }
            if (pack.material.kintsugiSeams) {
                drawLine(
                    color = pack.material.secondary.copy(alpha = 0.10f),
                    start = Offset(size.width * 0.10f, size.height),
                    end = Offset(size.width * 0.42f, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
                drawLine(
                    color = pack.material.secondary.copy(alpha = 0.07f),
                    start = Offset(size.width * 0.72f, size.height),
                    end = Offset(size.width * 0.90f, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            // Stable low-cost grain: deterministic points, recalculated only on size/theme changes.
            val random = Random(pack.id.hashCode() * 31 + size.width.toInt() + size.height.toInt())
            val grain = if (pack.palette.isLight) 0.018f else 0.045f
            repeat(260) {
                val point = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
                drawCircle(
                    color = if (random.nextBoolean()) Color.White.copy(alpha = grain) else Color.Black.copy(alpha = grain),
                    radius = random.nextFloat().coerceAtLeast(0.18f),
                    center = point,
                )
            }
        }
        content()
    }
}

/** Desktop premium card material: gradient, semantic border and 1dp top highlight. */
fun Modifier.desktopCard(
    active: Boolean = false,
    shape: Shape = RoundedCornerShape(NinetyRadius.md),
): Modifier {
    val pack = NinetyState.pack
    return this
        .background(
            Brush.verticalGradient(
                colors = if (active) {
                    listOf(pack.material.cardHoverTop, pack.material.cardBottom)
                } else {
                    listOf(pack.material.cardTop, pack.material.cardBottom)
                },
            ),
            shape,
        )
        .border(
            1.dp,
            if (active) pack.accentSoft.copy(alpha = 0.92f) else pack.material.border,
            shape,
        )
        .topHairline(alpha = if (pack.palette.isLight) 0.48f else 0.09f)
}

fun Modifier.desktopSidebar(): Modifier {
    val material = NinetyState.pack.material
    return background(Brush.verticalGradient(listOf(material.sidebarTop, material.sidebarBottom)))
        .border(1.dp, Ink.Line2)
}

fun Modifier.desktopNavRow(active: Boolean, shape: Shape): Modifier {
    val pack = NinetyState.pack
    val colors = if (active) {
        listOf(pack.material.rowStart, pack.material.rowActive, pack.material.rowMiddle)
    } else {
        listOf(pack.material.rowStart, pack.material.rowMiddle, pack.material.rowStart)
    }
    return background(Brush.horizontalGradient(colors), shape)
        .border(1.dp, if (active) pack.material.border else Ink.Line1, shape)
}

val DesktopActiveNavShape = GenericShape { size, _ ->
    val cut = 26.dp.toPx().coerceAtMost(size.height)
    moveTo(0f, 0f)
    lineTo(size.width - cut, 0f)
    lineTo(size.width, cut)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

val DesktopRowShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}
