package pw.x4.ninety.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Иконки разделов Настроек — порт desktop settings-view.js (stroke 1.5, lucide-style).
 * Только обводка (fill-кружки десктопа заменены на тонкие пути) — единый машинный стиль.
 */
object SettingsIcons {
    private fun icon(vararg paths: String): ImageVector {
        val b = ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        )
        for (d in paths) {
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val ArrowLeft: ImageVector = icon("m15 18-6-6 6-6")

    // Общие — три ползунка
    val General: ImageVector = icon(
        "M4 6h16", "M4 12h16", "M4 18h16",
        "M15 4.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z",
        "M9 10.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z",
        "M17 16.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z",
    )
    // Оформление — круг с разделением (тема)
    val Theme: ImageVector = icon("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M12 3v18")
    // Маршрутизация — узлы и связи
    val Routing: ImageVector = icon(
        "M6 4a2 2 0 1 0 0 4 2 2 0 0 0 0-4z",
        "M18 4a2 2 0 1 0 0 4 2 2 0 0 0 0-4z",
        "M12 16a2 2 0 1 0 0 4 2 2 0 0 0 0-4z",
        "M6 8v1a3 3 0 0 0 3 3h6a3 3 0 0 0 3-3V8", "M12 12v4",
    )
    // DNS — две стойки
    val Dns: ImageVector = icon(
        "M3 4h18v6H3z", "M3 14h18v6H3z", "M7 7h.01", "M7 17h.01", "M11 7h6", "M11 17h6",
    )
    // Локальный доступ — лоток входа
    val Inbound: ImageVector = icon(
        "M3 13v5a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-5",
        "M3 13l3-6a2 2 0 0 1 1.8-1h8.4a2 2 0 0 1 1.8 1l3 6",
        "M3 13h5l1.5 2.5h5L16 13h5",
    )
    // TLS — искра/фрагментация
    val Tls: ImageVector = icon(
        "M14 3l1.5 5L20 9.5 15.5 11 14 16l-1.5-5L8 9.5 12.5 8z",
        "M6.5 15l.7 2 1.8.8-1.8.8-.7 2-.7-2-1.8-.8 1.8-.8z",
    )
    // Мультиплексор — слияние
    val Mux: ImageVector = icon(
        "M3 6h6", "M3 12h6", "M3 18h6",
        "M9 6c4 0 4 6 7 6", "M9 12h7", "M9 18c4 0 4-6 7-6", "M16 12h5",
    )
    // О программе — инфо
    val Info: ImageVector = icon("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M12 11v5", "M12 8h.01")
}
