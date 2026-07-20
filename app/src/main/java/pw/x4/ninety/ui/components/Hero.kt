package pw.x4.ninety.ui.components

import android.animation.ValueAnimator
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure
import android.graphics.SurfaceTexture
import android.graphics.Typeface
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import pw.x4.ninety.R
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.vpn.ConnState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class Phase { Standby, Linking, Secured }

private fun ConnState.phase() = when (this) {
    ConnState.Idle -> Phase.Standby
    ConnState.Connecting, ConnState.Stopping -> Phase.Linking
    ConnState.Connected -> Phase.Secured
}

/** Pixel-geometry port of desktop `.hero__stage` + `hero-hud.js`. */
@Composable
fun Hero(
    state: ConnState,
    target: String?,
    modifier: Modifier = Modifier,
    stageSize: Dp = 320.dp,
    onToggle: () -> Unit,
) {
    val pack = NinetyState.pack
    val phase = state.phase()
    val secured = phase == Phase.Secured
    val lightHud = pack.id in LIGHT_HUD_THEMES
    val animationsEnabled = rememberAnimationsEnabled()

    val outerTransition = rememberInfiniteTransition(label = "desktopHero")
    val outerAngle by outerTransition.animateFloat(
        0f,
        if (animationsEnabled) 360f else 0f,
        infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart),
        label = "hudOuter",
    )
    val segmentAngle by outerTransition.animateFloat(
        if (animationsEnabled) 360f else 0f,
        0f,
        infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
        label = "hudSegments",
    )
    val ticksAngle by outerTransition.animateFloat(
        0f,
        if (animationsEnabled) 360f else 0f,
        infiniteRepeatable(tween(112_500, easing = LinearEasing), RepeatMode.Restart),
        label = "hudTicks",
    )

    val breathMs = when (phase) {
        Phase.Linking -> 1600
        Phase.Secured -> 4500
        Phase.Standby -> 6000
    }
    val breath by outerTransition.animateFloat(
        0f,
        if (animationsEnabled) 1f else 0f,
        infiniteRepeatable(tween(breathMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heroBreath",
    )

    var clock by remember { mutableStateOf(clockText()) }
    var integrity by remember { mutableIntStateOf(integrityFor(phase)) }
    var diagnosticIndex by remember { mutableIntStateOf(0) }
    var sysOpacity by remember { mutableStateOf(1f) }
    var glitchOffset by remember { mutableStateOf(0f) }
    var glitchOpacity by remember { mutableStateOf(1f) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = clockText()
            delay(1000)
        }
    }
    LaunchedEffect(phase, animationsEnabled) {
        integrity = integrityFor(phase)
        if (!animationsEnabled) return@LaunchedEffect
        while (true) {
            delay(1600)
            integrity = integrityFor(phase)
        }
    }
    LaunchedEffect(phase, animationsEnabled) {
        diagnosticIndex = 0
        if (!animationsEnabled) return@LaunchedEffect
        while (true) {
            delay(2400)
            diagnosticIndex++
        }
    }
    LaunchedEffect(animationsEnabled) {
        sysOpacity = 1f
        if (!animationsEnabled) return@LaunchedEffect
        while (true) {
            delay(1500)
            sysOpacity = 0.18f
            delay(105)
            sysOpacity = 1f
        }
    }
    LaunchedEffect(pack.id, animationsEnabled) {
        glitchOffset = 0f
        glitchOpacity = 1f
        if (animationsEnabled && !lightHud) {
            while (true) {
                delay(4000)
                if (Random.nextFloat() <= 0.6f) continue
                glitchOffset = 3f
                glitchOpacity = 0.55f
                delay(65)
                glitchOffset = -2f
                delay(85)
                glitchOffset = 0f
                glitchOpacity = 1f
            }
        }
    }

    val burst = remember { Animatable(1f) }
    LaunchedEffect(phase, animationsEnabled) {
        if (!animationsEnabled) {
            burst.snapTo(1f)
            return@LaunchedEffect
        }
        burst.snapTo(0f)
        burst.animateTo(1f, tween(if (secured) 900 else 600, easing = FastOutSlowInEasing))
    }
    val ripple = remember { Animatable(1f) }
    var rippleKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(rippleKey, animationsEnabled) {
        if (rippleKey == 0 || !animationsEnabled) {
            ripple.snapTo(1f)
            return@LaunchedEffect
        }
        ripple.snapTo(0f)
        ripple.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }

    val maskBrightnessTarget = when {
        pack.id == "porcelain" && secured -> 0.78f
        pack.id == "porcelain" -> 0.70f
        pack.id == "titanium" && secured -> 1.03f
        pack.id == "titanium" -> 0.82f
        pack.id == "kintsugi" && secured -> 1.02f
        pack.id == "kintsugi" -> 0.88f
        phase == Phase.Linking -> 1.05f
        secured -> 1.08f
        else -> 0.92f
    }
    val maskSaturationTarget = when {
        pack.id == "porcelain" -> if (secured) 0.42f else 0.28f
        pack.id == "titanium" -> if (secured) 0.82f else 0.32f
        pack.id == "kintsugi" -> if (secured) 0.88f else 0.62f
        pack.id == "aurora" -> if (secured) 1.18f else 0.92f
        phase == Phase.Linking -> 1.10f
        secured -> 1.05f
        else -> 0.92f
    }
    val colorTween = tween<Float>(if (animationsEnabled) 500 else 0)
    val maskBrightness by animateFloatAsState(maskBrightnessTarget, colorTween, label = "maskBrightness")
    val maskSaturation by animateFloatAsState(maskSaturationTarget, colorTween, label = "maskSaturation")

    val heroOpacity = when (phase) {
        Phase.Standby -> 0.50f
        Phase.Linking -> 0.82f
        Phase.Secured -> if (lightHud) 0.88f else 1f
    }
    val bloomOpacity = when (phase) {
        Phase.Standby -> if (lightHud) 0.10f else 0.16f
        Phase.Linking -> if (lightHud) 0.30f else 0.60f
        Phase.Secured -> if (lightHud) 0.24f else 0.80f
    }
    val haloOpacity = when (phase) {
        Phase.Standby -> if (lightHud) 0.20f else 0.50f
        Phase.Linking -> if (lightHud) 0.42f else 0.85f
        Phase.Secured -> if (lightHud) 0.34f else 0.90f
    }

    val diagnostics = if (secured) SECURED_DIAGNOSTICS else OFFLINE_DIAGNOSTICS
    val diagnostic = diagnostics[diagnosticIndex % diagnostics.size]
    val status = when (phase) {
        Phase.Secured -> "OPERATIONAL"
        Phase.Linking -> "LINKING"
        Phase.Standby -> "STAND-BY"
    }
    val targetLabel = if (secured) target?.takeIf(String::isNotBlank) ?: "190X4" else "UNKNOWN"
    val controlDescription = when (state) {
        ConnState.Idle -> "Подключить VPN"
        ConnState.Connecting -> "VPN подключается"
        ConnState.Connected -> "Отключить VPN"
        ConnState.Stopping -> "VPN отключается"
    }

    Box(modifier.size(stageSize), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(stageSize * 1.18f)
                .graphicsLayer {
                    scaleX = 1f + breath * 0.035f
                    scaleY = 1f + breath * 0.035f
                    alpha = bloomOpacity
                }
                .blur(42.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(pack.accentGlow, Color.Transparent))),
        )
        Box(
            Modifier
                .size(stageSize * 0.78f)
                .graphicsLayer {
                    scaleX = 1f + breath * 0.025f
                    scaleY = 1f + breath * 0.025f
                    alpha = haloOpacity
                }
                .blur(20.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        0f to pack.accentGlow,
                        0.35f to pack.accentSoft,
                        0.70f to Color.Transparent,
                    ),
                ),
        )

        Canvas(
            Modifier
                .size(stageSize * 1.14f)
                .graphicsLayer {
                    alpha = heroOpacity * glitchOpacity
                    translationX = glitchOffset
                },
        ) {
            val scale = size.minDimension / HUD_VIEWBOX
            val c = Offset(200f * scale, 200f * scale)
            fun r(value: Float) = value * scale

            drawCircle(color = pack.accentDeep, radius = r(98f), center = c, style = Stroke(r(1f)))
            drawCircle(color = Ink.Line2, radius = r(102f), center = c, style = Stroke(r(0.6f)))

            TARGET_ANGLES.forEach { angle ->
                drawHudArc(
                    center = c,
                    radius = r(190f),
                    start = angle - 13f,
                    sweep = 26f,
                    color = pack.accentBright,
                    width = r(2.6f),
                )
            }
            CARDINAL_ANGLES.forEach { angle ->
                drawRadialLine(c, r(198f), r(209f), angle, pack.accent, r(1.4f))
            }

            rotate(outerAngle, c) {
                drawCircle(color = Ink.TextFaint, radius = r(194f), center = c, style = Stroke(r(0.8f)))
                drawTickRing(c, count = 72, minorStart = r(187f), majorStart = r(182f), end = r(194f), majorEvery = 6)
            }
            rotate(segmentAngle, c) {
                repeat(5) { index ->
                    drawHudArc(
                        center = c,
                        radius = r(160f),
                        start = index * 72f,
                        sweep = 41.76f,
                        color = pack.accent,
                        width = r(2.6f),
                    )
                }
                drawCircle(
                    color = Ink.Line3,
                    radius = r(172f),
                    center = c,
                    style = Stroke(r(0.6f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(r(1.5f), r(5f)))),
                )
            }
            rotate(ticksAngle, c) {
                drawTickRing(c, count = 90, minorStart = r(118f), majorStart = r(114f), end = r(125f), majorEvery = 5)
            }

            drawCircle(color = Ink.Line2, radius = r(140f), center = c, style = Stroke(r(2f)))
            drawHudArc(
                center = c,
                radius = r(140f),
                start = -90f,
                sweep = integrity * 3.6f,
                color = pack.accentBright,
                width = r(2f),
            )

            if (burst.value < 1f) {
                val progress = burst.value
                drawCircle(
                    color = pack.accentBright.copy(alpha = (1f - progress) * 0.62f),
                    radius = r(98f + progress * 96f),
                    center = c,
                    style = Stroke(r(2f - progress.coerceAtMost(0.75f))),
                )
            }
            if (ripple.value < 1f) {
                val progress = ripple.value
                drawCircle(
                    color = pack.accentBright.copy(alpha = (1f - progress) * 0.84f),
                    radius = r(84f + progress * 112f),
                    center = c,
                    style = Stroke(r(2.2f - progress * 1.5f)),
                )
            }

            drawHudReadouts(
                scale = scale,
                status = "SYSTEM STATUS: $status",
                target = "TARGET LOCKED: ${targetLabel.uppercase().take(30)}",
                clock = clock,
                integrity = integrity,
                diagnostic = diagnostic,
                systemAlpha = sysOpacity,
                diagnosticColor = if (secured) Ink.TextLo else Ink.Err,
                glitch = animationsEnabled && !lightHud && glitchOffset != 0f,
                secondary = pack.material.secondary,
            )
        }

        val discScale = 1f + breath * 0.012f
        Box(
            Modifier
                .size(stageSize * 0.42f)
                .graphicsLayer {
                    scaleX = discScale
                    scaleY = discScale
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        0f to pack.material.discCenter,
                        0.70f to pack.material.discMiddle,
                        1f to pack.material.discEdge,
                    ),
                )
                .border(
                    1.dp,
                    if (secured) pack.material.border.copy(alpha = 0.92f) else Ink.Line2,
                    CircleShape,
                )
                .semantics {
                    role = Role.Button
                    contentDescription = controlDescription
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = state == ConnState.Idle || state == ConnState.Connected,
                ) {
                    if (animationsEnabled) rippleKey++
                    onToggle()
                },
            contentAlignment = Alignment.Center,
        ) {
            HeroMaskVideo(
                brightness = maskBrightness,
                saturation = maskSaturation,
                contrast = if (pack.id in setOf("porcelain", "titanium")) 1.18f else 1.05f,
                playbackEnabled = animationsEnabled,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun DrawScope.drawHudReadouts(
    scale: Float,
    status: String,
    target: String,
    clock: String,
    integrity: Int,
    diagnostic: String,
    systemAlpha: Float,
    diagnosticColor: Color,
    glitch: Boolean,
    secondary: Color,
) {
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val mono = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        val sans = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val top = AndroidPath().apply {
            moveTo(52f * scale, 200f * scale)
            arcTo(52f * scale, 52f * scale, 348f * scale, 348f * scale, 180f, 180f, false)
        }
        val bottom = AndroidPath().apply {
            moveTo(56f * scale, 200f * scale)
            arcTo(56f * scale, 56f * scale, 344f * scale, 344f * scale, 180f, -180f, false)
        }

        fun paint(size: Float, color: Color, alpha: Float = 1f, typeface: Typeface = mono) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.textSize = size * scale
                this.color = color.copy(alpha = color.alpha * alpha).toArgb()
                this.typeface = typeface
                textAlign = Paint.Align.CENTER
            }

        if (glitch) {
            val red = paint(10f, Color(0xFFE5484D), 0.36f)
            val cyan = paint(10f, Color(0xFF59F4E6), 0.30f)
            drawCenteredPathText(native, status, top, red, -1.1f * scale)
            drawCenteredPathText(native, status, top, cyan, 1.1f * scale)
        }

        drawCenteredPathText(native, status, top, paint(10f, Ink.TextHi, systemAlpha))
        drawCenteredPathText(native, target, bottom, paint(10f, secondary))
        native.drawText(clock, 200f * scale, 106f * scale, paint(9f, Ink.TextLo))
        native.drawText("INTEGRITY $integrity%", 200f * scale, 298f * scale, paint(16f, secondary, typeface = sans))
        native.drawText(diagnostic, 200f * scale, 372f * scale, paint(9f, diagnosticColor))
    }
}

private fun drawCenteredPathText(
    canvas: android.graphics.Canvas,
    text: String,
    path: AndroidPath,
    paint: Paint,
    extraOffset: Float = 0f,
) {
    val length = PathMeasure(path, false).length
    val offset = (length - paint.measureText(text)) / 2f + extraOffset
    canvas.drawTextOnPath(text, path, offset.coerceAtLeast(0f), 0f, paint)
}

private fun DrawScope.drawHudArc(
    center: Offset,
    radius: Float,
    start: Float,
    sweep: Float,
    color: Color,
    width: Float,
) {
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width),
    )
}

private fun DrawScope.drawTickRing(
    center: Offset,
    count: Int,
    minorStart: Float,
    majorStart: Float,
    end: Float,
    majorEvery: Int,
) {
    repeat(count) { index ->
        val major = index % majorEvery == 0
        val angle = index * (360f / count)
        drawRadialLine(
            center,
            if (major) majorStart else minorStart,
            end,
            angle,
            if (major) NinetyState.pack.accent else Ink.TextFaint,
            if (major) 1.3f else 0.7f,
        )
    }
}

private fun DrawScope.drawRadialLine(
    center: Offset,
    startRadius: Float,
    endRadius: Float,
    degrees: Float,
    color: Color,
    width: Float,
) {
    val angle = degrees * PI.toFloat() / 180f
    val x = cos(angle)
    val y = sin(angle)
    drawLine(
        color = color,
        start = Offset(center.x + x * startRadius, center.y + y * startRadius),
        end = Offset(center.x + x * endRadius, center.y + y * endRadius),
        strokeWidth = width,
    )
}

@Composable
private fun HeroMaskVideo(
    brightness: Float,
    saturation: Float,
    contrast: Float,
    playbackEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentPlaybackEnabled = rememberUpdatedState(playbackEnabled)
    val player = remember { MediaPlayer().apply { isLooping = true; setVolume(0f, 0f) } }
    var prepared by remember { mutableStateOf(false) }
    val layerPaint = remember { Paint() }
    val lastFilter = remember { floatArrayOf(Float.NaN, Float.NaN, Float.NaN) }

    val textureView = remember {
        var sourceConfigured = false
        TextureView(context).apply {
            isOpaque = true
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                    runCatching {
                        val surface = Surface(st)
                        player.setSurface(surface)
                        surface.release()
                        if (!sourceConfigured) {
                            context.resources.openRawResourceFd(R.raw.hero_mask).use { afd ->
                                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            }
                            player.setOnPreparedListener { mediaPlayer ->
                                runCatching {
                                    mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(0.7f)
                                }
                                prepared = true
                                if (
                                    currentPlaybackEnabled.value &&
                                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                                ) {
                                    runCatching { mediaPlayer.start() }
                                }
                            }
                            sourceConfigured = true
                            player.prepareAsync()
                        }
                    }
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit

                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    runCatching { player.setSurface(null) }
                    return true
                }

                override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> runCatching { if (player.isPlaying) player.pause() }
                Lifecycle.Event.ON_RESUME -> runCatching {
                    if (prepared && currentPlaybackEnabled.value && !player.isPlaying) player.start()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { player.release() }
        }
    }

    LaunchedEffect(playbackEnabled, prepared, lifecycleOwner.lifecycle.currentState) {
        if (!prepared) return@LaunchedEffect
        if (playbackEnabled && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            runCatching { if (!player.isPlaying) player.start() }
        } else {
            runCatching { if (player.isPlaying) player.pause() }
        }
    }

    AndroidView(
        factory = { textureView },
        modifier = modifier,
        update = { view ->
            if (
                lastFilter[0] != brightness ||
                lastFilter[1] != saturation ||
                lastFilter[2] != contrast
            ) {
                layerPaint.colorFilter = ColorMatrixColorFilter(
                    androidMaskMatrix(brightness, saturation, contrast),
                )
                view.setLayerType(View.LAYER_TYPE_HARDWARE, layerPaint)
                lastFilter[0] = brightness
                lastFilter[1] = saturation
                lastFilter[2] = contrast
            }
        },
    )
}

@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = ValueAnimator.areAnimatorsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return enabled
}

private fun androidMaskMatrix(
    brightness: Float,
    saturation: Float,
    contrast: Float,
): android.graphics.ColorMatrix {
    val offset = 127.5f * (1f - contrast)
    val result = android.graphics.ColorMatrix().apply { setSaturation(saturation) }
    result.postConcat(
        android.graphics.ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, offset,
                0f, contrast, 0f, 0f, offset,
                0f, 0f, contrast, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    result.postConcat(
        android.graphics.ColorMatrix(
            floatArrayOf(
                brightness, 0f, 0f, 0f, 0f,
                0f, brightness, 0f, 0f, 0f,
                0f, 0f, brightness, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    return result
}

private fun integrityFor(phase: Phase): Int = when (phase) {
    Phase.Secured -> Random.nextInt(85, 95)
    Phase.Linking -> Random.nextInt(40, 71)
    Phase.Standby -> Random.nextInt(0, 39)
}

private fun clockText(): String = LocalDateTime.now().format(CLOCK_FORMAT)

private const val HUD_VIEWBOX = 400f
private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd  HH:mm:ss")
private val TARGET_ANGLES = floatArrayOf(45f, 135f, 225f, 315f)
private val CARDINAL_ANGLES = floatArrayOf(0f, 90f, 180f, 270f)
private val LIGHT_HUD_THEMES = setOf("shiro", "sakura", "porcelain")
private val SECURED_DIAGNOSTICS = listOf("RTT STABLE", "TUN OK", "PKT_LOSS 0.0", "SYNC 0x4F", "LINK 190X4")
private val OFFLINE_DIAGNOSTICS = listOf("NO LINK", "SEARCHING…", "ERR_ON_KNW")
