package pw.x4.ninety.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import pw.x4.ninety.R
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.vpn.ConnState
import kotlin.math.cos
import kotlin.math.sin

/** Фаза hero (мэппинг ConnState → визуал desktop standby/linking/secured). */
private enum class Phase { Standby, Linking, Secured }

private fun ConnState.phase() = when (this) {
    ConnState.Idle -> Phase.Standby
    ConnState.Connecting, ConnState.Stopping -> Phase.Linking
    ConnState.Connected -> Phase.Secured
}

/**
 * Targeting-hero: порт `.hero__stage` из desktop-Ninety (app.css).
 * Слои снизу вверх: halo (radial glow + breath) → glow под диском → кольца 67/83/100%
 * → tickmarks → sweep-комета → burst/lock/ripple переходы → диск с маской oni.
 * Скорости дыхания/sweep зависят от фазы (standby медленно, linking быстро, secured плавно).
 */
@Composable
fun Hero(
    state: ConnState,
    modifier: Modifier = Modifier,
    stageSize: androidx.compose.ui.unit.Dp = 256.dp,
    onToggle: () -> Unit,
) {
    val pack = NinetyState.pack
    val phase = state.phase()

    // ── Бесконечные циклы: дыхание halo + вращение sweep, скорости по фазе ──
    val infinite = rememberInfiniteTransition(label = "hero")
    val breathMs = when (phase) { Phase.Linking -> 1600; Phase.Secured -> 4500; else -> 6000 }
    val sweepMs = when (phase) { Phase.Linking -> 2400; Phase.Secured -> 24000; else -> 12000 }

    val breath by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(breathMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val sweepAngle by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(sweepMs, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )

    // ── Переходы между состояниями: burst (вспышка), lock (фикс-кольцо secured) ──
    val burst = remember { Animatable(0f) }
    val lock = remember { Animatable(0f) }
    LaunchedEffect(phase) {
        burst.snapTo(0f)
        burst.animateTo(1f, tween(if (phase == Phase.Secured) 900 else 600, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(phase) {
        if (phase == Phase.Secured) lock.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        else lock.animateTo(0f, tween(400))
    }

    // ── Ripple по тапу диска ──
    val ripple = remember { Animatable(0f) }
    var rippleKey by remember { mutableStateOf(0) }
    LaunchedEffect(rippleKey) {
        if (rippleKey > 0) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
    }

    val secured = phase == Phase.Secured
    val accent = pack.accent
    val accentBright = pack.accentBright

    Box(modifier.size(stageSize), contentAlignment = Alignment.Center) {
        // ── Halo: мягкое радиальное свечение с дыханием ──
        val haloScale = 1f + breath * (if (secured) 0.04f else if (phase == Phase.Linking) 0.06f else 0.03f)
        val haloAlpha = (if (secured) 0.78f else if (phase == Phase.Linking) 0.7f else 0.5f) + breath * 0.22f
        Box(
            Modifier
                .size(stageSize * 0.86f)
                .graphicsLayer { scaleX = haloScale; scaleY = haloScale; alpha = haloAlpha }
                .blur(28.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        0.0f to pack.accentGlow,
                        0.35f to pack.accentSoft,
                        0.7f to Color.Transparent,
                    )
                )
        )

        // ── Векторные слои: glow, кольца, ticks, sweep, burst, lock, ripple ──
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val half = size.minDimension / 2f

            // glow под диском (замена box-shadow accent-glow)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to accent.copy(alpha = if (secured) 0.30f else 0.16f),
                    1f to Color.Transparent,
                    center = c, radius = half * 0.62f,
                ),
                radius = half * 0.62f, center = c,
            )

            // кольцо 100% (dashed, тусклое)
            drawCircle(
                color = Ink.Line1, radius = half - 1.5f, center = c,
                style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f))),
            )
            // кольцо 83%
            drawCircle(color = Ink.Line1, radius = half * 0.83f, center = c, style = Stroke(width = 1.2f))
            // ticks вокруг 83%
            drawTicks(c, half * 0.83f)
            // кольцо 67%
            drawCircle(color = Ink.Line2, radius = half * 0.67f, center = c, style = Stroke(width = 1.2f))

            // sweep-комета по внешнему кольцу
            val rOuter = half - 1.5f
            rotate(degrees = sweepAngle, pivot = c) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        0.78f to Color.Transparent,
                        1.0f to accent.copy(alpha = if (phase == Phase.Linking) 0.95f else 0.55f),
                        center = c,
                    ),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(c.x - rOuter, c.y - rOuter),
                    size = Size(rOuter * 2, rOuter * 2),
                    style = Stroke(width = 3f),
                )
            }

            // burst — вспышка при смене состояния
            if (burst.value < 1f && burst.value > 0f) {
                val p = burst.value
                val (scale, alpha) = when (phase) {
                    Phase.Secured -> lerp(0.55f, 1.4f, p) to (1f - p) * 0.9f
                    Phase.Linking -> lerp(1.08f, 0.55f, p) to (1f - p) * 0.7f
                    else -> lerp(1f, 1.18f, p) to (1f - p) * 0.5f
                }
                drawCircle(
                    color = accentBright.copy(alpha = alpha.coerceIn(0f, 1f)),
                    radius = half * scale, center = c, style = Stroke(width = 2f),
                )
            }

            // lock-ring — тонкое фикс-кольцо в secured
            if (lock.value > 0f) {
                drawCircle(
                    color = accent.copy(alpha = 0.45f * lock.value),
                    radius = half * 0.83f, center = c, style = Stroke(width = 1.4f),
                )
            }

            // ripple от тапа
            if (ripple.value > 0f && ripple.value < 1f) {
                val p = ripple.value
                drawCircle(
                    color = accentBright.copy(alpha = (1f - p) * 0.9f),
                    radius = half * 0.54f * lerp(1f, 2.2f, p), center = c,
                    style = Stroke(width = lerp(2f, 0.5f, p)),
                )
            }
        }

        // ── Диск с маской oni ──
        val discSize = stageSize * 0.54f
        Box(
            Modifier
                .size(discSize)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        0.0f to Ink.Ink2,
                        0.7f to Ink.Ink1,
                        1.0f to Ink.Ink0,
                    )
                )
                .border(1.dp, if (secured) Ink.Line3 else Ink.Line2, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { rippleKey++; onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            // лёгкая десатурация в standby, полная насыщенность + accent-вуаль в secured
            val sat = if (secured) 1.05f else 0.92f
            Image(
                painter = painterResource(R.drawable.oni_mask),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(sat) }),
            )
            if (secured) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                0.55f to Color.Transparent,
                                1.0f to pack.accentGlow,
                            )
                        )
                )
            }
        }
    }
}

/** Деления-радар по кольцу: 60 минорных, мажорные каждые 5. */
private fun DrawScope.drawTicks(center: Offset, radius: Float) {
    val count = 60
    for (i in 0 until count) {
        val major = i % 5 == 0
        val a = (i.toFloat() / count) * (2f * Math.PI).toFloat() - (Math.PI / 2f).toFloat()
        val len = if (major) radius * 0.06f else radius * 0.035f
        val r0 = radius - len
        val cosA = cos(a); val sinA = sin(a)
        drawLine(
            color = if (major) Ink.TextLo else Ink.TextFaint,
            start = Offset(center.x + cosA * r0, center.y + sinA * r0),
            end = Offset(center.x + cosA * radius, center.y + sinA * radius),
            strokeWidth = if (major) 1.4f else 1f,
        )
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
