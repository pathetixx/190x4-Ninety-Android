package pw.x4.ninety.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Полный порт `src/styles/tokens.css` desktop-Ninety.
 *
 * В отличие от раннего Android-порта тема теперь меняет не только accent, но и
 * нейтрали, линии, текст и материал поверхности. Это позволяет точно переносить
 * Shiro/Sakura/Glacier/Midnight/Ronin/Amber, не имитируя их одним цветным акцентом.
 */
data class NinetyPalette(
    val isLight: Boolean,
    val ink0: Color,
    val ink1: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    val line1: Color,
    val line2: Color,
    val line3: Color,
    val edgeTop: Color,
    val overlay1: Color,
    val overlay2: Color,
    val overlay3: Color,
    val overlay4: Color,
    val shine1: Color,
    val shine2: Color,
    val shadowStrong: Color,
    val textHi: Color,
    val textMid: Color,
    val textLo: Color,
    val textFaint: Color,
    val accent: Color,
    val accentBright: Color,
    val accentDeep: Color,
    val accentSoft: Color,
    val accentGlow: Color,
    val ok: Color = Color(0xFF4ADE80),
    val warn: Color = Color(0xFFF5B544),
    val err: Color = Color(0xFFE5484D),
)

/**
 * Каталог темы. Старые свойства accent* оставлены как computed API, чтобы экраны
 * постепенно мигрировали без массового рискованного переписывания.
 */
data class ThemePack(
    val id: String,
    val label: String,
    val kicker: String,
    val palette: NinetyPalette,
) {
    val accent: Color get() = palette.accent
    val accentBright: Color get() = palette.accentBright
    val accentDeep: Color get() = palette.accentDeep
    val accentSoft: Color get() = palette.accentSoft
    val accentGlow: Color get() = palette.accentGlow
}

private val BaseDark = NinetyPalette(
    isLight = false,
    ink0 = Color(0xFF08080A),
    ink1 = Color(0xFF0E0E11),
    ink2 = Color(0xFF15151A),
    ink3 = Color(0xFF1D1D24),
    ink4 = Color(0xFF25252D),
    line1 = Color(0x0AFFFFFF),
    line2 = Color(0x12FFFFFF),
    line3 = Color(0x1CFFFFFF),
    edgeTop = Color(0x14FFFFFF),
    overlay1 = Color(0x06FFFFFF),
    overlay2 = Color(0x0AFFFFFF),
    overlay3 = Color(0x0FFFFFFF),
    overlay4 = Color(0x1AFFFFFF),
    shine1 = Color(0x0DFFFFFF),
    shine2 = Color(0x14FFFFFF),
    shadowStrong = Color(0xB3000000),
    textHi = Color(0xFFF5F5F2),
    textMid = Color(0xFFA3A3A8),
    textLo = Color(0xFF6B6B72),
    textFaint = Color(0xFF46464C),
    accent = Color(0xFFC0304A),
    accentBright = Color(0xFFDE5772),
    accentDeep = Color(0xFF6E1A28),
    accentSoft = Color(0x24C0304A),
    accentGlow = Color(0x52C0304A),
)

private fun darkAccentPack(
    id: String,
    label: String,
    kicker: String,
    accent: Long,
    bright: Long,
    deep: Long,
    soft: Long,
    glow: Long,
): ThemePack = ThemePack(
    id = id,
    label = label,
    kicker = kicker,
    palette = BaseDark.copy(
        accent = Color(accent),
        accentBright = Color(bright),
        accentDeep = Color(deep),
        accentSoft = Color(soft),
        accentGlow = Color(glow),
    ),
)

val ThemePacks: List<ThemePack> = listOf(
    darkAccentPack(
        id = "kurogane", label = "Kurogane", kicker = "NEON · RED",
        accent = 0xFFC0304A, bright = 0xFFDE5772, deep = 0xFF6E1A28,
        soft = 0x24C0304A, glow = 0x52C0304A,
    ),
    ThemePack(
        id = "shiro", label = "Shiro", kicker = "LIGHT · LACQUER",
        palette = NinetyPalette(
            isLight = true,
            ink0 = Color(0xFFFAFAF8), ink1 = Color(0xFFF5F4F0),
            ink2 = Color(0xFFEEECE6), ink3 = Color(0xFFE0DDD5), ink4 = Color(0xFFD1CDC3),
            line1 = Color(0x0E231F1A), line2 = Color(0x1A231F1A), line3 = Color(0x26231F1A),
            edgeTop = Color(0x6BFFFFFF),
            overlay1 = Color(0x06231F1A), overlay2 = Color(0x0A231F1A),
            overlay3 = Color(0x0F231F1A), overlay4 = Color(0x1A231F1A),
            shine1 = Color(0x61FFFFFF), shine2 = Color(0x85FFFFFF),
            shadowStrong = Color(0x2B302A22),
            textHi = Color(0xFF24211D), textMid = Color(0xFF625D55),
            textLo = Color(0xFF91897D), textFaint = Color(0xFFB7B0A4),
            accent = Color(0xFFB52F48), accentBright = Color(0xFFCC5268),
            accentDeep = Color(0xFF7A1F31), accentSoft = Color(0x1CB52F48),
            accentGlow = Color(0x2EB52F48),
        ),
    ),
    ThemePack(
        id = "sakura", label = "Sakura Haze", kicker = "LIGHT · HAZE",
        palette = NinetyPalette(
            isLight = true,
            ink0 = Color(0xFFF4EEF2), ink1 = Color(0xFFECE2E8),
            ink2 = Color(0xFFE3D5DD), ink3 = Color(0xFFD2C0CA), ink4 = Color(0xFFC5AFBC),
            line1 = Color(0x0F1D141A), line2 = Color(0x1A1D141A), line3 = Color(0x291D141A),
            edgeTop = Color(0x75FFFFFF),
            overlay1 = Color(0x061D141A), overlay2 = Color(0x0A1D141A),
            overlay3 = Color(0x0F1D141A), overlay4 = Color(0x1A1D141A),
            shine1 = Color(0x61FFFFFF), shine2 = Color(0x85FFFFFF),
            shadowStrong = Color(0x3D5C4A32),
            textHi = Color(0xFF1D141A), textMid = Color(0xFF66545D),
            textLo = Color(0xFF967E8A), textFaint = Color(0xFFB9A2AE),
            accent = Color(0xFFC45072), accentBright = Color(0xFFEA83A2),
            accentDeep = Color(0xFF84314A), accentSoft = Color(0x21C45072),
            accentGlow = Color(0x3DC45072),
        ),
    ),
    darkAccentPack(
        id = "cyan", label = "Cyan", kicker = "ELECTRIC · SAFE",
        accent = 0xFF1FD6D6, bright = 0xFF6CF2F2, deep = 0xFF0C7373,
        soft = 0x241FD6D6, glow = 0x6B1FD6D6,
    ),
    ThemePack(
        id = "glacier", label = "Glacier", kicker = "STEEL · CYAN",
        palette = BaseDark.copy(
            ink0 = Color(0xFF061014), ink1 = Color(0xFF0B181E),
            ink2 = Color(0xFF11242B), ink3 = Color(0xFF1A333B), ink4 = Color(0xFF22444E),
            line1 = Color(0x0ADCFAFF), line2 = Color(0x13DCFAFF), line3 = Color(0x1FDCFAFF),
            edgeTop = Color(0x14DCFAFF),
            textHi = Color(0xFFF0FCFF), textMid = Color(0xFFA3C8D3),
            textLo = Color(0xFF5D7D86), textFaint = Color(0xFF395862),
            accent = Color(0xFF76E6FF), accentBright = Color(0xFFB9F4FF),
            accentDeep = Color(0xFF28798B), accentSoft = Color(0x2476E6FF),
            accentGlow = Color(0x5776E6FF),
        ),
    ),
    ThemePack(
        id = "midnight", label = "Midnight", kicker = "DEEP · INDIGO",
        palette = BaseDark.copy(
            ink0 = Color(0xFF070911), ink1 = Color(0xFF0D1120),
            ink2 = Color(0xFF141B31), ink3 = Color(0xFF202B48), ink4 = Color(0xFF2A3659),
            line1 = Color(0x0AFFFFFF), line2 = Color(0x13FFFFFF), line3 = Color(0x1FFFFFFF),
            textHi = Color(0xFFF2F5FF), textMid = Color(0xFFAAB5D8),
            textLo = Color(0xFF626D90), textFaint = Color(0xFF3F4968),
            accent = Color(0xFF7E96FF), accentBright = Color(0xFFB8C6FF),
            accentDeep = Color(0xFF354A9B), accentSoft = Color(0x247E96FF),
            accentGlow = Color(0x577E96FF),
        ),
    ),
    darkAccentPack(
        id = "synthwave", label = "Synthwave", kicker = "VIOLET · WAVE",
        accent = 0xFFC77DFF, bright = 0xFFE0A6FF, deep = 0xFF7A3FA0,
        soft = 0x24C77DFF, glow = 0x52C77DFF,
    ),
    ThemePack(
        id = "ronin", label = "Ronin Violet", kicker = "NOIR · VIOLET",
        palette = BaseDark.copy(
            ink0 = Color(0xFF09070E), ink1 = Color(0xFF100D17),
            ink2 = Color(0xFF1A1424), ink3 = Color(0xFF271B36), ink4 = Color(0xFF332348),
            line1 = Color(0x0AFFF0FF), line2 = Color(0x13FFF0FF), line3 = Color(0x1FFFF0FF),
            edgeTop = Color(0x14FFF0FF),
            textHi = Color(0xFFF9F2FF), textMid = Color(0xFFB9A7C9),
            textLo = Color(0xFF735F84), textFaint = Color(0xFF4B3B5A),
            accent = Color(0xFFA66CFF), accentBright = Color(0xFFD2AEFF),
            accentDeep = Color(0xFF57308C), accentSoft = Color(0x24A66CFF),
            accentGlow = Color(0x52A66CFF),
        ),
    ),
    darkAccentPack(
        id = "matrix", label = "Matrix", kicker = "EMERALD",
        accent = 0xFF2BD66A, bright = 0xFF5CEE92, deep = 0xFF1A8C45,
        soft = 0x242BD66A, glow = 0x522BD66A,
    ),
    ThemePack(
        id = "amber", label = "Amber Glass", kicker = "BLACK · GOLD",
        palette = BaseDark.copy(
            ink0 = Color(0xFF0A0805), ink1 = Color(0xFF12100B),
            ink2 = Color(0xFF1B160E), ink3 = Color(0xFF2A2113), ink4 = Color(0xFF372B18),
            line1 = Color(0x0AFFE6B4), line2 = Color(0x13FFE6B4), line3 = Color(0x1FFFE6B4),
            edgeTop = Color(0x14FFE7B8),
            textHi = Color(0xFFFFF7E7), textMid = Color(0xFFC8B99C),
            textLo = Color(0xFF7F7056), textFaint = Color(0xFF514631),
            accent = Color(0xFFE0A750), accentBright = Color(0xFFFFD07A),
            accentDeep = Color(0xFF7D541C), accentSoft = Color(0x24E0A750),
            accentGlow = Color(0x4DE0A750),
        ),
    ),
    darkAccentPack(
        id = "mono", label = "Mono", kicker = "MONOCHROME",
        accent = 0xFFE8E8EE, bright = 0xFFFFFFFF, deep = 0xFF9A9AA6,
        soft = 0x14E8E8EE, glow = 0x33FFFFFF,
    ),
    darkAccentPack(
        id = "command", label = "Command Center", kicker = "CRIMSON · COMMAND",
        accent = 0xFFE60026, bright = 0xFFFF3355, deep = 0xFF720013,
        soft = 0x26E60026, glow = 0x6BFF2D46,
    ),
)

fun packById(id: String?): ThemePack = ThemePacks.firstOrNull { it.id == id } ?: ThemePacks.first()

/** Один observable-снимок токенов для legacy-компонентов, использующих [Ink]. */
private object NinetyThemeTokens {
    var palette by mutableStateOf(ThemePacks.first().palette)
}

/**
 * Совместимый facade для существующих экранов. Все значения теперь динамические:
 * смена ThemePack обновляет нейтрали и текст так же, как desktop data-theme.
 */
object Ink {
    val Ink0: Color get() = NinetyThemeTokens.palette.ink0
    val Ink1: Color get() = NinetyThemeTokens.palette.ink1
    val Ink2: Color get() = NinetyThemeTokens.palette.ink2
    val Ink3: Color get() = NinetyThemeTokens.palette.ink3
    val Ink4: Color get() = NinetyThemeTokens.palette.ink4

    val Line1: Color get() = NinetyThemeTokens.palette.line1
    val Line2: Color get() = NinetyThemeTokens.palette.line2
    val Line3: Color get() = NinetyThemeTokens.palette.line3
    val EdgeTop: Color get() = NinetyThemeTokens.palette.edgeTop

    val Overlay1: Color get() = NinetyThemeTokens.palette.overlay1
    val Overlay2: Color get() = NinetyThemeTokens.palette.overlay2
    val Overlay3: Color get() = NinetyThemeTokens.palette.overlay3
    val Overlay4: Color get() = NinetyThemeTokens.palette.overlay4
    val Shine1: Color get() = NinetyThemeTokens.palette.shine1
    val Shine2: Color get() = NinetyThemeTokens.palette.shine2
    val ShadowStrong: Color get() = NinetyThemeTokens.palette.shadowStrong

    val TextHi: Color get() = NinetyThemeTokens.palette.textHi
    val TextMid: Color get() = NinetyThemeTokens.palette.textMid
    val TextLo: Color get() = NinetyThemeTokens.palette.textLo
    val TextFaint: Color get() = NinetyThemeTokens.palette.textFaint

    val Ok: Color get() = NinetyThemeTokens.palette.ok
    val Warn: Color get() = NinetyThemeTokens.palette.warn
    val Err: Color get() = NinetyThemeTokens.palette.err
}

object NinetyState {
    private var currentPack by mutableStateOf(ThemePacks.first())

    var pack: ThemePack
        get() = currentPack
        set(value) {
            currentPack = value
            NinetyThemeTokens.palette = value.palette
        }
}

/** Desktop radius scale: 6 / 10 / 14 / 18 / 24. */
object NinetyRadius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 18.dp
    val xl = 24.dp
}

/** Desktop spacing scale: 4 / 8 / 12 / 16 / 24 / 32 / 48 / 64. */
object NinetySpacing {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 24.dp
    val x6 = 32.dp
    val x7 = 48.dp
    val x8 = 64.dp
}

private val NinetyShapes = Shapes(
    extraSmall = RoundedCornerShape(NinetyRadius.xs),
    small = RoundedCornerShape(NinetyRadius.sm),
    medium = RoundedCornerShape(NinetyRadius.md),
    large = RoundedCornerShape(NinetyRadius.lg),
    extraLarge = RoundedCornerShape(NinetyRadius.xl),
)

@Composable
fun NinetyTheme(content: @Composable () -> Unit) {
    val pack = NinetyState.pack
    val p = pack.palette
    val scheme = if (p.isLight) {
        lightColorScheme(
            primary = p.accent,
            onPrimary = p.ink0,
            secondary = p.accentBright,
            onSecondary = p.ink0,
            background = p.ink0,
            onBackground = p.textHi,
            surface = p.ink1,
            onSurface = p.textHi,
            surfaceVariant = p.ink2,
            onSurfaceVariant = p.textMid,
            outline = p.line3,
            error = p.err,
        )
    } else {
        darkColorScheme(
            primary = p.accent,
            onPrimary = p.ink0,
            secondary = p.accentBright,
            onSecondary = p.ink0,
            background = p.ink0,
            onBackground = p.textHi,
            surface = p.ink1,
            onSurface = p.textHi,
            surfaceVariant = p.ink2,
            onSurfaceVariant = p.textMid,
            outline = p.line3,
            error = p.err,
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = NinetyTypography,
        shapes = NinetyShapes,
        content = content,
    )
}
