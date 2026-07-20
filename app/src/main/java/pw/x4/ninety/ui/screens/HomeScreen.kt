package pw.x4.ninety.ui.screens

import androidx.compose.runtime.Composable
import pw.x4.ninety.ui.layout.NinetyLayoutMetrics

@Composable
fun HomeScreen(
    metrics: NinetyLayoutMetrics,
    onToggle: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenNodes: () -> Unit,
) {
    DesktopHomeScreen(
        metrics = metrics,
        onToggle = onToggle,
        onOpenProfiles = onOpenProfiles,
        onOpenNodes = onOpenNodes,
    )
}
