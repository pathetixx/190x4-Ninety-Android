package pw.x4.ninety.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pw.x4.ninety.ui.screens.ConnectScreen
import pw.x4.ninety.ui.screens.NodesScreen
import pw.x4.ninety.ui.screens.SettingsScreen
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.NinetyState

private enum class Dest(val label: String, val icon: ImageVector) {
    Connect("Подключение", Icons.Rounded.Bolt),
    Nodes("Узлы", Icons.Rounded.Hub),
    Settings("Настройки", Icons.Rounded.Settings),
}

@Composable
fun NinetyApp(onToggleVpn: () -> Unit) {
    var dest by rememberSaveable { mutableStateOf(Dest.Connect) }

    Scaffold(
        containerColor = Ink.Ink0,
        bottomBar = { NinetyBottomBar(current = dest, onSelect = { dest = it }) },
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            when (dest) {
                Dest.Connect -> ConnectScreen(onToggle = onToggleVpn)
                Dest.Nodes -> NodesScreen()
                Dest.Settings -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun NinetyBottomBar(current: Dest, onSelect: (Dest) -> Unit) {
    val pack = NinetyState.pack
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Ink.Ink0.copy(alpha = 0f),
                    0.35f to Ink.Ink1,
                    1f to Ink.Ink1,
                )
            )
            .navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line2))
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dest.entries.forEach { d ->
                val active = d == current
                val tint by animateColorAsState(
                    if (active) pack.accent else Ink.TextLo,
                    tween(220), label = "navtint",
                )
                Column(
                    Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(d) }
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // активный индикатор — короткая accent-черта над иконкой
                    Box(
                        Modifier
                            .height(2.dp)
                            .width(if (active) 18.dp else 0.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (active) pack.accent else Color.Transparent)
                    )
                    Spacer(Modifier.height(6.dp))
                    Icon(d.icon, contentDescription = d.label, tint = tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(
                        d.label.uppercase(),
                        color = tint,
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
