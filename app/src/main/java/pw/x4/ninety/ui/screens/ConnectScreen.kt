package pw.x4.ninety.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import pw.x4.ninety.ui.components.Kicker
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.vpn.ConnState
import pw.x4.ninety.vpn.VpnController

@Composable
fun ConnectScreen() {
    val pack = NinetyState.pack
    val state = VpnController.state

    val statusText = when (state) {
        ConnState.Idle -> "Не защищено"
        ConnState.Connecting -> "Подключение…"
        ConnState.Connected -> "Защищено"
        ConnState.Stopping -> "Отключение…"
    }

    // Дыхание/вращение колец — безусловный вызов (Compose-грабля: не в if).
    val transition = rememberInfiniteTransition(label = "hero")
    val sweep by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Kicker("Ninety", accent = true)
        Spacer(Modifier.height(8.dp))
        Text(
            statusText,
            style = pw.x4.ninety.ui.theme.NinetyTypography.headlineMedium,
            color = if (state == ConnState.Connected) pack.accentBright else Ink.TextHi,
        )

        Spacer(Modifier.height(40.dp))

        Box(
            Modifier
                .size(232.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { VpnController.toggle() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val active = state == ConnState.Connected
                val ringColor = if (active) pack.accent else Ink.Line3
                // внешнее targeting-кольцо
                drawCircle(color = ringColor, radius = size.minDimension / 2f - 6f, center = c, style = Stroke(width = 3f))
                // внутреннее кольцо
                drawCircle(color = if (active) pack.accentBright else Ink.Line2, radius = size.minDimension / 3.2f, center = c, style = Stroke(width = 2f))
                // вращающаяся дуга-метка
                val r = size.minDimension / 2f - 6f
                drawArc(
                    color = pack.accent,
                    startAngle = sweep,
                    sweepAngle = 28f,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                    style = Stroke(width = 4f),
                )
                // центральная точка-glow
                drawCircle(color = if (active) pack.accentGlow else Ink.Ink3, radius = size.minDimension / 5f, center = c)
            }
            Text(
                if (state == ConnState.Connected || state == ConnState.Connecting) "ОТКЛЮЧИТЬ" else "ПОДКЛЮЧИТЬ",
                style = MonoStyle,
                color = if (state == ConnState.Connected) pack.accentBright else Ink.TextHi,
            )
        }

        Spacer(Modifier.height(40.dp))
        Text(
            VpnController.activeServer ?: "Сервер не выбран",
            style = MonoStyle,
            color = Ink.TextMid,
        )
    }
}
