package pw.x4.ninety.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import pw.x4.ninety.core.model.RoutingRule
import pw.x4.ninety.ui.components.DesktopConfirmDialog

/** Adds the desktop destructive-action gate without coupling it to the rule editor internals. */
@Composable
internal fun DesktopRoutingRulesGate(
    rules: List<RoutingRule>,
    onRulesChange: (List<RoutingRule>) -> Unit,
) {
    var pendingDeletion by remember { mutableStateOf<List<RoutingRule>?>(null) }

    RoutingRulesEditor(rules) { candidate ->
        if (candidate.size < rules.size) {
            pendingDeletion = candidate
        } else {
            onRulesChange(candidate)
        }
    }

    pendingDeletion?.let { candidate ->
        val removedCount = (rules.size - candidate.size).coerceAtLeast(1)
        DesktopConfirmDialog(
            kicker = "Routing · Delete",
            title = if (removedCount == 1) "Удалить правило?" else "Удалить правила?",
            message = if (removedCount == 1) {
                "Правило маршрутизации будет удалено. Изменение вступит в силу после перезапуска VPN."
            } else {
                "Будет удалено правил: $removedCount. Изменения вступят в силу после перезапуска VPN."
            },
            confirmLabel = "Удалить",
            destructive = true,
            onDismiss = { pendingDeletion = null },
            onConfirm = { onRulesChange(candidate) },
        )
    }
}
