package pw.x4.ninety.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Product breakpoints shared by the shell and every screen. */
enum class NinetyWindowClass {
    Compact,
    Medium,
    Expanded,
}

@Immutable
data class NinetyLayoutMetrics(
    val windowClass: NinetyWindowClass,
    val pagePadding: Dp,
    val maxContentWidth: Dp,
    val navigationWidth: Dp,
) {
    val isCompact: Boolean get() = windowClass == NinetyWindowClass.Compact
    val isExpanded: Boolean get() = windowClass == NinetyWindowClass.Expanded
}

fun layoutMetrics(width: Dp): NinetyLayoutMetrics = when {
    width < 600.dp -> NinetyLayoutMetrics(
        windowClass = NinetyWindowClass.Compact,
        pagePadding = 16.dp,
        maxContentWidth = 720.dp,
        navigationWidth = 0.dp,
    )

    width < 960.dp -> NinetyLayoutMetrics(
        windowClass = NinetyWindowClass.Medium,
        pagePadding = 24.dp,
        maxContentWidth = 820.dp,
        navigationWidth = 84.dp,
    )

    else -> NinetyLayoutMetrics(
        windowClass = NinetyWindowClass.Expanded,
        pagePadding = 32.dp,
        maxContentWidth = 1180.dp,
        navigationWidth = 236.dp,
    )
}

/** Centers content on large tablets/desktop windows while preserving edge-to-edge backgrounds. */
@Composable
fun NinetyPage(
    metrics: NinetyLayoutMetrics,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = contentAlignment) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .widthIn(max = metrics.maxContentWidth)
                .padding(horizontal = metrics.pagePadding),
            content = content,
        )
    }
}
