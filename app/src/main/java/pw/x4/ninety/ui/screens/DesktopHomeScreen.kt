package pw.x4.ninety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.components.Hero
import pw.x4.ninety.ui.layout.NinetyLayoutMetrics
import pw.x4.ninety.ui.layout.NinetyPage
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.vpn.ClashMonitor
import pw.x4.ninety.vpn.ConnState
import pw.x4.ninety.vpn.ProbePhase
import pw.x4.ninety.vpn.TunnelModes
import pw.x4.ninety.vpn.VpnController

@Composable
internal fun DesktopHomeScreen(
    metrics: NinetyLayoutMetrics,
    onToggle: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenNodes: () -> Unit,
) {
    val state = VpnController.state
    val monitor = ClashMonitor.snapshot
    val profile = Store.activeProfile()
    val target = desktopEffectiveNodeName(monitor.autoNow)
    var sessionSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(state) {
        sessionSeconds = 0
        if (state == ConnState.Connected) {
            while (true) {
                delay(1000)
                sessionSeconds++
            }
        }
    }

    NinetyPage(metrics) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = if (metrics.isCompact) 14.dp else 20.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DesktopSubscriptionToolbar(
                profile = profile,
                onOpenProfiles = onOpenProfiles,
            )
            Spacer(Modifier.height(if (metrics.isCompact) 18.dp else 12.dp))

            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val stage = desktopHeroStageSize(metrics.isCompact, maxWidth)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Hero(
                        state = state,
                        target = target,
                        stageSize = stage,
                        onToggle = onToggle,
                    )
                    Spacer(Modifier.height(if (metrics.isCompact) 4.dp else 8.dp))
                    DesktopHeroStatus(state = state, target = target)
                }
            }

            Spacer(Modifier.height(16.dp))
            DesktopActiveRouteCard(
                target = target,
                delay = monitor.effectiveDelay(),
                onClick = onOpenNodes,
            )
            Spacer(Modifier.height(10.dp))
            DesktopTelemetryStrip(
                compact = metrics.isCompact,
                sessionSeconds = sessionSeconds,
            )

            VpnController.lastError?.let { error ->
                Spacer(Modifier.height(10.dp))
                DesktopErrorStrip(error)
            }
            monitor.lastError?.takeIf { state == ConnState.Connected }?.let { error ->
                Spacer(Modifier.height(8.dp))
                DesktopErrorStrip(error, warning = monitor.phase != ProbePhase.Error)
            }
        }
    }
}

@Composable
private fun DesktopHeroStatus(state: ConnState, target: String?) {
    val pack = NinetyState.pack
    val title = when (state) {
        ConnState.Idle -> "Готов к подключению"
        ConnState.Connecting -> "Устанавливаю защищённый канал"
        ConnState.Connected -> target ?: "Защищённое соединение"
        ConnState.Stopping -> "Останавливаю туннель"
    }
    val kicker = when (state) {
        ConnState.Idle -> "ENGINE · STANDBY"
        ConnState.Connecting -> "SYSTEM · LINKING"
        ConnState.Connected -> "SECURED · ${TunnelModes.current().title.uppercase()}"
        ConnState.Stopping -> "ENGINE · DISCONNECTING"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            style = NinetyTypography.headlineMedium,
            color = if (state == ConnState.Connected) pack.material.status else Ink.TextHi,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(
                        when (state) {
                            ConnState.Connected -> pack.accentBright
                            ConnState.Connecting, ConnState.Stopping -> Ink.Warn
                            ConnState.Idle -> Ink.TextMid
                        },
                        CircleShape,
                    ),
            )
            Spacer(Modifier.size(9.dp))
            Text(kicker, style = KickerStyle, color = Ink.TextLo)
        }
    }
}
