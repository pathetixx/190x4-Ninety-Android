package pw.x4.ninety.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Порт токенов из web-Ninety (src/styles/tokens.css).
 * Нейтрали общие — темы переключаются только акцентом (kurogane/synthwave/matrix/mono).
 */
object Ink {
    val Ink0 = Color(0xFF08080A)
    val Ink1 = Color(0xFF0E0E11)
    val Ink2 = Color(0xFF15151A)
    val Ink3 = Color(0xFF1D1D24)
    val Ink4 = Color(0xFF25252D)

    val Line1 = Color(0x0AFFFFFF) // rgba(255,255,255,.04)
    val Line2 = Color(0x12FFFFFF) // .07
    val Line3 = Color(0x1CFFFFFF) // .11

    val TextHi = Color(0xFFF5F5F2)
    val TextMid = Color(0xFFA3A3A8)
    val TextLo = Color(0xFF6B6B72)
    val TextFaint = Color(0xFF46464C)

    val Ok = Color(0xFF4ADE80)
    val Warn = Color(0xFFF5B544)
    val Err = Color(0xFFE5484D)
}

/** Палитра-пак: меняется только акцент. accentSoft/Glow — для подложек и свечения. */
data class ThemePack(
    val id: String,
    val label: String,
    val accent: Color,
    val accentBright: Color,
    val accentDeep: Color,
    val accentSoft: Color,
    val accentGlow: Color,
)

val ThemePacks: List<ThemePack> = listOf(
    ThemePack(
        id = "kurogane", label = "Kurogane",
        accent = Color(0xFFC0304A), accentBright = Color(0xFFDE5772), accentDeep = Color(0xFF6E1A28),
        accentSoft = Color(0x24C0304A), accentGlow = Color(0x52C0304A),
    ),
    ThemePack(
        id = "synthwave", label = "Synthwave",
        accent = Color(0xFFC77DFF), accentBright = Color(0xFFE0A6FF), accentDeep = Color(0xFF7A3FA0),
        accentSoft = Color(0x24C77DFF), accentGlow = Color(0x52C77DFF),
    ),
    ThemePack(
        id = "matrix", label = "Matrix",
        accent = Color(0xFF2BD66A), accentBright = Color(0xFF5CEE92), accentDeep = Color(0xFF1A8C45),
        accentSoft = Color(0x242BD66A), accentGlow = Color(0x522BD66A),
    ),
    ThemePack(
        id = "mono", label = "Mono",
        accent = Color(0xFFE8E8EE), accentBright = Color(0xFFFFFFFF), accentDeep = Color(0xFF9A9AA6),
        accentSoft = Color(0x14E8E8EE), accentGlow = Color(0x33FFFFFF),
    ),
)

fun packById(id: String?): ThemePack = ThemePacks.firstOrNull { it.id == id } ?: ThemePacks.first()

/**
 * Активный пак как State (паттерн hub190x4 HubColors.pack): любой composable,
 * читающий [NinetyState.pack], авто-перекомпозится при смене темы — экраны не трогаем.
 */
object NinetyState {
    var pack by mutableStateOf(ThemePacks.first())
}

@Composable
fun NinetyTheme(content: @Composable () -> Unit) {
    val pack = NinetyState.pack
    val scheme = darkColorScheme(
        primary = pack.accent,
        onPrimary = Ink.Ink0,
        secondary = pack.accentBright,
        background = Ink.Ink0,
        onBackground = Ink.TextHi,
        surface = Ink.Ink1,
        onSurface = Ink.TextHi,
        surfaceVariant = Ink.Ink2,
        onSurfaceVariant = Ink.TextMid,
        outline = Ink.Line3,
        error = Ink.Err,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = NinetyTypography,
        content = content,
    )
}
