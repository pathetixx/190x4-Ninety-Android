package pw.x4.ninety.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pw.x4.ninety.ui.components.Kicker
import pw.x4.ninety.ui.theme.Ink

@Composable
fun NodesScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Kicker("Узлы", accent = true)
        Spacer(Modifier.height(12.dp))
        Text(
            "Нет подписок",
            style = pw.x4.ninety.ui.theme.NinetyTypography.titleLarge,
            color = Ink.TextHi,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Импорт подписки и конфигов появится в следующем релизе:\nссылка ninety://, буфер обмена, список нод с пингом.",
            color = Ink.TextMid,
            textAlign = TextAlign.Center,
            style = pw.x4.ninety.ui.theme.NinetyTypography.bodyMedium,
        )
    }
}
