package pw.x4.ninety.ui.components

import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
 * Targeting-hero: порт `.hero__stage` из desktop-Ninety (app.css + index.html).
 * Слои снизу вверх: halo (radial glow + breath) → glow под диском (box-shadow 32→60px)
 * → кольца 67/83/100% → tickmarks → sweep-комета → burst/lock/ripple переходы → диск с
 * АНИМИРОВАННОЙ маской самурая (webm, как `<video class=hero__mask>`).
 * Скорости дыхания/sweep и фильтр маски зависят от фазы (standby/linking/secured).
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

    // Per-state фильтр маски (порт .hero__mask из app.css: brightness/saturate/contrast).
    // Плавный кросс-фейд 500ms как `transition: filter` desktop.
    val targetBright = when (phase) { Phase.Linking -> 1.05f; Phase.Secured -> 1.08f; else -> 0.92f }
    val targetSat = when (phase) { Phase.Linking -> 1.10f; Phase.Secured -> 1.05f; else -> 0.92f }
    val maskBright by animateFloatAsState(targetBright, tween(500, easing = FastOutSlowInEasing), label = "maskBright")
    val maskSat by animateFloatAsState(targetSat, tween(500, easing = FastOutSlowInEasing), label = "maskSat")

    // Свечение диска: box-shadow accent-glow 32px (standby) → 60px (secured) — главный
    // off/on сигнал. Анимируем радиус+насыщенность glow под диском плавно 500ms.
    val glowTarget = when (phase) { Phase.Secured -> 1f; Phase.Linking -> 0.6f; else -> 0.25f }
    val glow by animateFloatAsState(glowTarget, tween(500, easing = FastOutSlowInEasing), label = "discGlow")

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

            // glow под диском (замена box-shadow accent-glow 32→60px): растёт с состоянием
            val glowRadius = half * lerp(0.55f, 0.74f, glow)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to accent.copy(alpha = lerp(0.12f, 0.34f, glow)),
                    1f to Color.Transparent,
                    center = c, radius = glowRadius,
                ),
                radius = glowRadius, center = c,
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

        // ── Диск с анимированной маской самурая (webm) ──
        val discSize = stageSize * 0.54f
        val maskScale = 1f + breath * 0.015f
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
            HeroMaskVideo(
                brightness = maskBright,
                saturation = maskSat,
                contrast = 1.05f,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = maskScale; scaleY = maskScale },
            )
        }
    }
}

/**
 * Анимированная маска самурая — порт `<video class="hero__mask">` desktop:
 * локальный webm из res/raw, луп, без звука, скорость 0.7 (как `playbackRate` desktop).
 * Рендер в [TextureView] (клипуется в кружок родителем, в отличие от SurfaceView).
 * Per-state фильтр (brightness/saturate/contrast) — через hardware-layer
 * [ColorMatrixColorFilter] на самой View. Пауза/релиз по жизненному циклу — батарея.
 */
@Composable
private fun HeroMaskVideo(
    brightness: Float,
    saturation: Float,
    contrast: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember { MediaPlayer().apply { isLooping = true; setVolume(0f, 0f) } }
    var prepared by remember { mutableStateOf(false) }
    val layerPaint = remember { Paint() }
    // last-applied фильтр: setLayerType дёргаем только при изменении (иначе каждый кадр).
    val lastFilter = remember { floatArrayOf(Float.NaN, Float.NaN) }

    val textureView = remember {
        TextureView(context).apply {
            isOpaque = true
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    runCatching {
                        player.setSurface(Surface(st))
                        context.resources.openRawResourceFd(R.raw.hero_mask).use { afd ->
                            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        }
                        player.setOnPreparedListener { mp ->
                            runCatching { mp.playbackParams = mp.playbackParams.setSpeed(0.7f) }
                            runCatching { mp.start() }
                            prepared = true
                        }
                        player.prepareAsync()
                    }
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> runCatching { if (player.isPlaying) player.pause() }
                Lifecycle.Event.ON_RESUME -> runCatching { if (prepared && !player.isPlaying) player.start() }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            runCatching { player.release() }
        }
    }

    AndroidView(
        factory = { textureView },
        modifier = modifier,
        update = { tv ->
            if (lastFilter[0] != brightness || lastFilter[1] != saturation) {
                layerPaint.colorFilter = ColorMatrixColorFilter(androidMaskMatrix(brightness, saturation, contrast))
                tv.setLayerType(View.LAYER_TYPE_HARDWARE, layerPaint)
                lastFilter[0] = brightness; lastFilter[1] = saturation
            }
        },
    )
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

/**
 * Фильтр маски как CSS `.hero__mask`: saturation → contrast (вокруг середины) → brightness.
 * android.graphics.ColorMatrix (шкала 0..255), offset контраста = 127.5·(1−c).
 */
private fun androidMaskMatrix(brightness: Float, saturation: Float, contrast: Float): android.graphics.ColorMatrix {
    val o = 127.5f * (1f - contrast)
    val out = android.graphics.ColorMatrix().apply { setSaturation(saturation) }
    out.postConcat(
        android.graphics.ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, o,
                0f, contrast, 0f, 0f, o,
                0f, 0f, contrast, 0f, o,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
    out.postConcat(
        android.graphics.ColorMatrix(
            floatArrayOf(
                brightness, 0f, 0f, 0f, 0f,
                0f, brightness, 0f, 0f, 0f,
                0f, 0f, brightness, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
    return out
}
