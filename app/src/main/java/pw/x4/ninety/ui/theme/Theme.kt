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
 * Android source of truth for the desktop Ninety token and material registry.
 *
 * Material 3 is only the Compose infrastructure. Surfaces, borders, sidebar rows,
 * hero disc and HUD use the same semantic layers as desktop `tokens.css`,
 * `app.css` and the premium theme styles.
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

data class NinetyMaterial(
    val cardTop: Color,
    val cardBottom: Color,
    val cardHoverTop: Color,
    val cardHoverBottom: Color,
    val discCenter: Color,
    val discMiddle: Color,
    val discEdge: Color,
    val border: Color,
    val secondary: Color,
    val status: Color,
    val sidebarTop: Color,
    val sidebarBottom: Color,
    val rowStart: Color,
    val rowMiddle: Color,
    val rowHover: Color,
    val rowActive: Color,
    val sidebarText: Color,
    val sidebarTextActive: Color,
    val appSecondaryGlow: Color = Color.Transparent,
    val grid: Boolean = false,
    val kintsugiSeams: Boolean = false,
)

private fun defaultMaterial(p: NinetyPalette) = NinetyMaterial(
    cardTop = p.ink2,
    cardBottom = p.ink1,
    cardHoverTop = p.ink3,
    cardHoverBottom = p.ink2,
    discCenter = p.ink2,
    discMiddle = p.ink1,
    discEdge = p.ink0,
    border = p.line2,
    secondary = p.accentBright,
    status = p.accentBright,
    sidebarTop = p.ink2,
    sidebarBottom = p.ink0,
    rowStart = p.ink1,
    rowMiddle = p.ink2,
    rowHover = p.ink3,
    rowActive = p.accentSoft,
    sidebarText = p.textMid,
    sidebarTextActive = p.textHi,
)

data class ThemePack(
    val id: String,
    val label: String,
    val kicker: String,
    val palette: NinetyPalette,
    val material: NinetyMaterial = defaultMaterial(palette),
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

private val Kurogane = darkAccentPack(
    id = "kurogane", label = "Kurogane", kicker = "NEON · RED",
    accent = 0xFFC0304A, bright = 0xFFDE5772, deep = 0xFF6E1A28,
    soft = 0x24C0304A, glow = 0x52C0304A,
)

private val ShiroPalette = NinetyPalette(
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
)

private val SakuraPalette = NinetyPalette(
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
)

private fun premiumPack(
    id: String,
    label: String,
    kicker: String,
    palette: NinetyPalette,
    material: NinetyMaterial,
) = ThemePack(id, label, kicker, palette, material)

val ThemePacks: List<ThemePack> = listOf(
    Kurogane,
    ThemePack("shiro", "Shiro Light", "LIGHT · PREMIUM", ShiroPalette),
    ThemePack("sakura", "Sakura Haze", "SOFT · ROSE", SakuraPalette),
    darkAccentPack(
        "cyan", "Cyan", "SECURED · CYAN",
        0xFF1FD6D6, 0xFF6CF2F2, 0xFF0C7373, 0x241FD6D6, 0x6B1FD6D6,
    ),
    ThemePack(
        "glacier", "Glacier", "ICE · STEEL",
        BaseDark.copy(
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
        "midnight", "Midnight Indigo", "MIDNIGHT · INDIGO",
        BaseDark.copy(
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
        "synthwave", "Synthwave", "VIOLET WAVE",
        0xFFC77DFF, 0xFFE0A6FF, 0xFF7A3FA0, 0x24C77DFF, 0x52C77DFF,
    ),
    ThemePack(
        "ronin", "Ronin Violet", "NOIR · VIOLET",
        BaseDark.copy(
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
        "matrix", "Matrix", "EMERALD",
        0xFF2BD66A, 0xFF5CEE92, 0xFF1A8C45, 0x242BD66A, 0x522BD66A,
    ),
    ThemePack(
        "amber", "Amber Glass", "BLACK · GOLD",
        BaseDark.copy(
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
        "mono", "Mono", "MONOCHROME",
        0xFFE8E8EE, 0xFFFFFFFF, 0xFF9A9AA6, 0x14E8E8EE, 0x33FFFFFF,
    ),
    darkAccentPack(
        "command", "Command Center", "CMD · CRIMSON",
        0xFFE60026, 0xFFFF3355, 0xFF720013, 0x26E60026, 0x6BFF2D46,
    ),
    premiumPack(
        id = "kintsugi", label = "Kintsugi Noir", kicker = "LACQUER · GOLD",
        palette = BaseDark.copy(
            ink0 = Color(0xFF050404), ink1 = Color(0xFF0C0909),
            ink2 = Color(0xFF171111), ink3 = Color(0xFF241818), ink4 = Color(0xFF332020),
            line1 = Color(0x0EE0C58B), line2 = Color(0x1FE0C58B), line3 = Color(0x38E0C58B),
            edgeTop = Color(0x17FFE8BE),
            overlay1 = Color(0x06FFEECD), overlay2 = Color(0x0DFFEECD),
            overlay3 = Color(0x14FFEECD), overlay4 = Color(0x21FFEECD),
            shine1 = Color(0x0FFFF0C4), shine2 = Color(0x21FFEBC4),
            shadowStrong = Color(0xD1000000),
            textHi = Color(0xFFF5EEE7), textMid = Color(0xFFB9AA9D),
            textLo = Color(0xFF786C64), textFaint = Color(0xFF4B403B),
            accent = Color(0xFFE84D5B), accentBright = Color(0xFFFF7180),
            accentDeep = Color(0xFF7F202B), accentSoft = Color(0x24E84D5B),
            accentGlow = Color(0x61E84D5B),
            ok = Color(0xFFE84D5B), warn = Color(0xFFD8B56B),
        ),
        material = NinetyMaterial(
            cardTop = Color(0xFF1F1614), cardBottom = Color(0xFF0A0808),
            cardHoverTop = Color(0xFF2A1C19), cardHoverBottom = Color(0xFF0F0A0A),
            discCenter = Color(0xFF241A16), discMiddle = Color(0xFF0E0A09), discEdge = Color(0xFF050404),
            border = Color(0x3DD8B56B), secondary = Color(0xFFD8B56B), status = Color(0xFFE5C887),
            sidebarTop = Color(0xFF15100F), sidebarBottom = Color(0xFF070505),
            rowStart = Color(0xFF090707), rowMiddle = Color(0xFF100C0B),
            rowHover = Color(0xFF1A1211), rowActive = Color(0xFF2A1517),
            sidebarText = Color(0xFFA99889), sidebarTextActive = Color(0xFFF5EEE7),
            appSecondaryGlow = Color(0x17D8B56B), kintsugiSeams = true,
        ),
    ),
    premiumPack(
        id = "aurora", label = "Aurora Glass", kicker = "AURORA · GLASS",
        palette = BaseDark.copy(
            ink0 = Color(0xFF02070D), ink1 = Color(0xFF071421),
            ink2 = Color(0xFF0D2233), ink3 = Color(0xFF15364B), ink4 = Color(0xFF204B63),
            line1 = Color(0x0BBDEFFF), line2 = Color(0x1ABDEFFF), line3 = Color(0x2EBDEFFF),
            edgeTop = Color(0x1FE2FBFF),
            overlay1 = Color(0x06D5F9FF), overlay2 = Color(0x0DD5F9FF),
            overlay3 = Color(0x14D5F9FF), overlay4 = Color(0x1FD5F9FF),
            shine1 = Color(0x0FE2FBFF), shine2 = Color(0x1FE2FBFF),
            shadowStrong = Color(0xCC00020A),
            textHi = Color(0xFFEFFCFF), textMid = Color(0xFFA7C6D5),
            textLo = Color(0xFF638394), textFaint = Color(0xFF385568),
            accent = Color(0xFF59F4E6), accentBright = Color(0xFFA0FFF6),
            accentDeep = Color(0xFF168F91), accentSoft = Color(0x2159F4E6),
            accentGlow = Color(0x6B59F4E6), ok = Color(0xFF59F4E6),
        ),
        material = NinetyMaterial(
            cardTop = Color(0xCC112B3E), cardBottom = Color(0xE005121F),
            cardHoverTop = Color(0xE0183950), cardHoverBottom = Color(0xF0071828),
            discCenter = Color(0xFF143B50), discMiddle = Color(0xFF071A2A), discEdge = Color(0xFF020912),
            border = Color(0x3397E1FF), secondary = Color(0xFF8EA7FF), status = Color(0xFF8CFFF5),
            sidebarTop = Color(0xF20B1F2F), sidebarBottom = Color(0xFA030D16),
            rowStart = Color(0xED04111D), rowMiddle = Color(0xF0091D2D),
            rowHover = Color(0xF50F2E43), rowActive = Color(0xF5123E54),
            sidebarText = Color(0xFF9AB7C8), sidebarTextActive = Color(0xFFF3FDFF),
            appSecondaryGlow = Color(0x2E8EA7FF),
        ),
    ),
    premiumPack(
        id = "porcelain", label = "Porcelain Zero", kicker = "PORCELAIN · INK",
        palette = NinetyPalette(
            isLight = true,
            ink0 = Color(0xFFF1ECE3), ink1 = Color(0xFFFBF8F1),
            ink2 = Color(0xFFE8E1D6), ink3 = Color(0xFFD9D0C3), ink4 = Color(0xFFC8BDAE),
            line1 = Color(0x0E372E27), line2 = Color(0x1C372E27), line3 = Color(0x2E372E27),
            edgeTop = Color(0xB8FFFFFF),
            overlay1 = Color(0x06372E27), overlay2 = Color(0x0C372E27),
            overlay3 = Color(0x12372E27), overlay4 = Color(0x1C372E27),
            shine1 = Color(0x73FFFFFF), shine2 = Color(0xB8FFFFFF),
            shadowStrong = Color(0x33493B2E),
            textHi = Color(0xFF28231F), textMid = Color(0xFF675E56),
            textLo = Color(0xFF94897E), textFaint = Color(0xFFB8AEA2),
            accent = Color(0xFFC94246), accentBright = Color(0xFFD9585B),
            accentDeep = Color(0xFF842E31), accentSoft = Color(0x1AC94246),
            accentGlow = Color(0x2EC94246),
            ok = Color(0xFFC94246), warn = Color(0xFFA9864D), err = Color(0xFFB43B3E),
        ),
        material = NinetyMaterial(
            cardTop = Color(0xFFFFFDF8), cardBottom = Color(0xFFEFE9E0),
            cardHoverTop = Color(0xFFFFFDF8), cardHoverBottom = Color(0xFFEAE2D8),
            discCenter = Color(0xFFFFFDF8), discMiddle = Color(0xFFECE5DB), discEdge = Color(0xFFDED5C8),
            border = Color(0x24544539), secondary = Color(0xFFA9864D), status = Color(0xFF28231F),
            sidebarTop = Color(0xFFEEE8DE), sidebarBottom = Color(0xFFDDD5C8),
            rowStart = Color(0xE0F7F3EC), rowMiddle = Color(0xEBEAE3D9),
            rowHover = Color(0xEBE0D6CA), rowActive = Color(0xF5F9EFEB),
            sidebarText = Color(0xFF6E655D), sidebarTextActive = Color(0xFF28231F),
            appSecondaryGlow = Color(0x0FA9864D),
        ),
    ),
    premiumPack(
        id = "titanium", label = "Titanium Signal", kicker = "TITANIUM · SIGNAL",
        palette = BaseDark.copy(
            ink0 = Color(0xFF070B0F), ink1 = Color(0xFF0D141B),
            ink2 = Color(0xFF17212A), ink3 = Color(0xFF25323D), ink4 = Color(0xFF34434F),
            line1 = Color(0x09D4E1EA), line2 = Color(0x16D4E1EA), line3 = Color(0x26D4E1EA),
            edgeTop = Color(0x17E8F4FB),
            overlay1 = Color(0x05DEEEF7), overlay2 = Color(0x0CDEEEF7),
            overlay3 = Color(0x13DEEEF7), overlay4 = Color(0x1FDEEEF7),
            shine1 = Color(0x0EE8F4FB), shine2 = Color(0x1AE8F4FB),
            shadowStrong = Color(0xD1000000),
            textHi = Color(0xFFEDF3F7), textMid = Color(0xFFA8B4BD),
            textLo = Color(0xFF687782), textFaint = Color(0xFF3F4C56),
            accent = Color(0xFF4EB8FF), accentBright = Color(0xFF8DD2FF),
            accentDeep = Color(0xFF17699E), accentSoft = Color(0x1F4EB8FF),
            accentGlow = Color(0x574EB8FF), ok = Color(0xFF5DD58C),
        ),
        material = NinetyMaterial(
            cardTop = Color(0xFF202A34), cardBottom = Color(0xFF0A1016),
            cardHoverTop = Color(0xFF2A3642), cardHoverBottom = Color(0xFF0D151C),
            discCenter = Color(0xFF2A3540), discMiddle = Color(0xFF111922), discEdge = Color(0xFF070B0F),
            border = Color(0x24C7D5DF), secondary = Color(0xFFC7D5DF), status = Color(0xFF5DD58C),
            sidebarTop = Color(0xFF1B222A), sidebarBottom = Color(0xFF090D12),
            rowStart = Color(0xFF0D1318), rowMiddle = Color(0xFF151E25),
            rowHover = Color(0xFF202C35), rowActive = Color(0xFF263743),
            sidebarText = Color(0xFFA0ADB6), sidebarTextActive = Color(0xFFF2F6F8),
            appSecondaryGlow = Color(0x123F8FBD), grid = true,
        ),
    ),
)

fun packById(id: String?): ThemePack = ThemePacks.firstOrNull { it.id == id } ?: ThemePacks.first()

private object NinetyThemeTokens {
    var palette by mutableStateOf(ThemePacks.first().palette)
}

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

object NinetyRadius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 18.dp
    val xl = 24.dp
}

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
    val p = NinetyState.pack.palette
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
