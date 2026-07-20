package pw.x4.ninety

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pw.x4.ninety.ui.icons.SettingsIcons
import pw.x4.ninety.ui.screens.WarpSettingsPane
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.NinetyTheme
import pw.x4.ninety.ui.theme.NinetyTypography

class WarpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NinetyTheme {
                WarpSetupScreen(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun WarpSetupScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Ink0)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(Ink.Ink2, CircleShape).clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SettingsIcons.ArrowLeft, "Назад", tint = Ink.TextMid, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("CLOUDFLARE · WARP", style = KickerStyle, color = Ink.TextFaint)
                Spacer(Modifier.height(4.dp))
                Text("Настройка WARP", style = NinetyTypography.headlineMedium, color = Ink.TextHi)
            }
        }
        Spacer(Modifier.height(18.dp))
        WarpSettingsPane()
        Spacer(Modifier.height(28.dp))
    }
}
