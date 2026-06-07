package pw.x4.ninety.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pw.x4.ninety.data.Node
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.components.ScreenHeader
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography

@Composable
fun NodesScreen() {
    val context = LocalContext.current
    val profile = Store.activeProfile()
    val nodes = Store.activeProfileNodes()

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        ScreenHeader(
            kicker = "Nodes" + (profile?.name?.let { " · $it" } ?: ""),
            title = "Ноды",
            sub = if (nodes.isEmpty()) "Профиль не выбран" else "${nodes.size} ${plural(nodes.size)} · нажмите для выбора сервера",
        )
        Spacer(Modifier.height(16.dp))

        if (nodes.isEmpty()) {
            Text(
                "Нет нод. Добавьте профиль во вкладке «Профили» —\nего серверы появятся здесь.",
                color = Ink.TextMid, style = NinetyTypography.bodyMedium,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(nodes, key = { it.id }) { node ->
                    NodeRow(node, selected = node.id == Store.activeId) {
                        if (node.supported) Store.setActive(node.id)
                        else Toast.makeText(context, "xhttp пока не поддержан (xray, M3)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeRow(node: Node, selected: Boolean, onClick: () -> Unit) {
    val pack = NinetyState.pack
    val enabled = node.supported
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) pack.accentSoft else Ink.Ink1, RoundedCornerShape(14.dp))
            .border(1.dp, if (selected) pack.accent else Ink.Line2, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(10.dp).background(if (selected) pack.accent else Ink.Line3, CircleShape)
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                node.name.ifBlank { node.host },
                color = if (enabled) Ink.TextHi else Ink.TextLo,
                style = NinetyTypography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${node.proto} · ${node.host}:${node.port}" + if (!enabled) " · xray (M3)" else "",
                color = Ink.TextLo, style = MonoStyle,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.size(10.dp))
            Text(
                "АКТИВЕН", style = MonoStyle, color = pack.accent,
                modifier = Modifier
                    .background(pack.accentSoft, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

private fun plural(n: Int): String {
    val mod10 = n % 10; val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "нода"
        mod10 in 2..4 && mod100 !in 12..14 -> "ноды"
        else -> "нод"
    }
}
