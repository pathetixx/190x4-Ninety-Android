package pw.x4.ninety.ui.screens

import android.content.ClipboardManager
import android.content.Context
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
import pw.x4.ninety.data.Importer
import pw.x4.ninety.data.Node
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.components.Kicker
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography

@Composable
fun NodesScreen() {
    val context = LocalContext.current
    val pack = NinetyState.pack
    val nodes = Store.nodes

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Kicker("Узлы", accent = true)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Серверы", style = NinetyTypography.headlineMedium, color = Ink.TextHi)
            Text(
                "ДОБАВИТЬ ИЗ БУФЕРА",
                style = MonoStyle,
                color = pack.accent,
                modifier = Modifier
                    .background(pack.accentSoft, RoundedCornerShape(8.dp))
                    .border(1.dp, pack.accent, RoundedCornerShape(8.dp))
                    .clickable { addFromClipboard(context) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        if (nodes.isEmpty()) {
            Text(
                "Скопируйте ссылку (vless/vmess/trojan/ss/hysteria2/tuic) или подписку\nи нажмите «Добавить из буфера».",
                color = Ink.TextMid,
                style = NinetyTypography.bodyMedium,
            )
        } else {
            LazyColumn {
                items(nodes, key = { it.id }) { node ->
                    NodeRow(node, selected = node.id == Store.activeId) {
                        if (node.supported) Store.setActive(node.id)
                        else Toast.makeText(context, "xhttp пока не поддержан (xray, M3)", Toast.LENGTH_SHORT).show()
                    }
                    Spacer(Modifier.height(8.dp))
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
            .background(
                if (selected) pack.accentSoft else Ink.Ink2,
                RoundedCornerShape(14.dp),
            )
            .border(
                1.dp,
                if (selected) pack.accent else Ink.Line2,
                RoundedCornerShape(14.dp),
            )
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(if (selected) pack.accent else Ink.Line3, CircleShape)
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                node.name.ifBlank { node.host },
                color = if (enabled) Ink.TextHi else Ink.TextLo,
                style = NinetyTypography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${node.proto} · ${node.host}:${node.port}" + if (!enabled) " · xray (M3)" else "",
                color = Ink.TextLo,
                style = MonoStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun addFromClipboard(context: Context) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
    Importer.importText(
        raw = text,
        onLoading = { Toast.makeText(context, "Загружаю подписку…", Toast.LENGTH_SHORT).show() },
        onDone = { added, error ->
            val msg = error ?: if (added > 0) "Добавлено узлов: $added" else "Ничего не добавлено"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        },
    )
}
