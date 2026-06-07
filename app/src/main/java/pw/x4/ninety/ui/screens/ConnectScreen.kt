package pw.x4.ninety.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pw.x4.ninety.ui.components.Hero
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.vpn.ConnState
import pw.x4.ninety.vpn.VpnController

@Composable
fun ConnectScreen(onToggle: () -> Unit) {
    val pack = NinetyState.pack
    val state = VpnController.state

    val title = when (state) {
        ConnState.Idle -> "Не защищено"
        ConnState.Connecting -> "Подключение…"
        ConnState.Connected -> "Защищено"
        ConnState.Stopping -> "Отключение…"
    }
    val hint = when (state) {
        ConnState.Idle -> "STAND-BY · ОТКЛЮЧЕНО"
        ConnState.Connecting -> "ПОИСК КАНАЛА…"
        ConnState.Connected -> "КАНАЛ ЗАЩИЩЁН"
        ConnState.Stopping -> "ЗАВЕРШЕНИЕ…"
    }
    val secured = state == ConnState.Connected

    // text-in: при смене состояния заголовок «всплывает» (порт hero-text-in).
    val textIn = remember { Animatable(0f) }
    LaunchedEffect(state) {
        textIn.snapTo(0f)
        textIn.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }

    // пульс точки-кикера
    val pulse = rememberInfiniteTransition(label = "kpulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "dot",
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Hero(state = state, onToggle = onToggle)

        Spacer(Modifier.height(28.dp))

        Text(
            title,
            style = NinetyTypography.headlineMedium,
            color = if (secured) pack.accentBright else Ink.TextHi,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                alpha = textIn.value
                translationY = (1f - textIn.value) * 14f
            },
        )

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background((if (secured) pack.accent else Ink.TextLo).copy(alpha = dotAlpha))
            )
            Spacer(Modifier.size(8.dp))
            Text(hint, style = KickerStyle, color = Ink.TextLo)
        }

        Spacer(Modifier.height(20.dp))

        Text(
            VpnController.activeServer
                ?: pw.x4.ninety.data.Store.activeNode()?.name
                ?: "Сервер не выбран",
            style = MonoStyle,
            color = Ink.TextMid,
            textAlign = TextAlign.Center,
        )
        VpnController.lastError?.let {
            if (state == ConnState.Idle) {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MonoStyle, color = Ink.Err, textAlign = TextAlign.Center)
            }
        }
    }
}
