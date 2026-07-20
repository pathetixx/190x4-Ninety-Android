package pw.x4.ninety.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Иконки 1-в-1 с desktop-Ninety (те же SVG-path из index.html, stroke 1.5 round).
 * Строим ImageVector из path-строк; цвет задаёт Icon(tint=…). Так вкладки/карточки/
 * настройки выглядят как на Windows, без подбора Material-аналогов.
 */
object NinetyIcons {
    private fun icon(vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (data in paths) {
            builder.addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    // ── Навигация ──
    val Home: ImageVector = icon(
        "M3 10.5 12 3l9 7.5",
        "M5 9.5V20a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V9.5",
    )
    val Profiles: ImageVector = icon(
        "m12 2 9 5-9 5-9-5 9-5z",
        "m3 12 9 5 9-5",
        "m3 17 9 5 9-5",
    )
    val Nodes: ImageVector = icon("M13 2 3 14h7l-1 8 10-12h-7l1-8z")
    val Settings: ImageVector = icon(
        "M12 9a3 3 0 1 0 0 6 3 3 0 1 0 0-6z",
        "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z",
    )

    // ── Мелкие ──
    val Globe: ImageVector = icon(
        "M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18z",
        "M3 12h18",
        "M12 3a14 14 0 0 1 0 18 14 14 0 0 1 0-18z",
    )
    val File: ImageVector = icon(
        "M14 3v4a1 1 0 0 0 1 1h4",
        "M17 21H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7l5 5v11a2 2 0 0 1-2 2z",
    )
    val More: ImageVector = icon("M5 12h.01", "M12 12h.01", "M19 12h.01")
    val ChevronRight: ImageVector = icon("m9 6 6 6-6 6")
    val Plus: ImageVector = icon("M12 5v14", "M5 12h14")
    val Refresh: ImageVector = icon(
        "M3 12a9 9 0 0 1 15.5-6.4L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-15.5 6.4L3 16",
        "M3 21v-5h5",
    )
    val Logs: ImageVector = icon("m4 17 6-6-6-6", "M12 19h8")
    val Trash: ImageVector = icon(
        "M3 6h18",
        "M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2",
        "M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6",
    )
    val Shield: ImageVector = icon("M12 22c5-2.2 8-5.5 8-10V5l-8-3-8 3v7c0 4.5 3 7.8 8 10z")
    val Sliders: ImageVector = icon(
        "M4 6h10", "M10 12h10", "M4 18h10",
        "M17 4v4", "M7 10v4", "M17 16v4",
    )
    val Wifi: ImageVector = icon(
        "M5 12.5a11 11 0 0 1 14 0",
        "M2 9a16 16 0 0 1 20 0",
        "M8.5 16a6 6 0 0 1 7 0",
        "M12 19.5h.01",
    )
}
