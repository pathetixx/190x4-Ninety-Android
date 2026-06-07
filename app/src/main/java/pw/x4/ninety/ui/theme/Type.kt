package pw.x4.ninety.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pw.x4.ninety.R

/**
 * Типографика порта desktop-Ninety. Display = Inter Tight (как `--font-display`),
 * Mono = JetBrains Mono (`--font-mono`). TTF собраны из woff2-сабсетов (latin+cyrillic)
 * fontTools-мёржем, лежат в res/font. Системные шрифты больше не используются.
 */
val InterTight = FontFamily(
    Font(R.font.inter_tight_400, FontWeight.Normal),
    Font(R.font.inter_tight_500, FontWeight.Medium),
    Font(R.font.inter_tight_600, FontWeight.SemiBold),
    Font(R.font.inter_tight_700, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_400, FontWeight.Normal),
    Font(R.font.jetbrains_mono_500, FontWeight.Medium),
    Font(R.font.jetbrains_mono_600, FontWeight.SemiBold),
)

val NinetyTypography = Typography(
    displayLarge = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Medium, fontSize = 24.sp, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Medium, fontSize = 13.sp),
)

/** Кикер — uppercase, разреженный, как .kicker в tokens.css. Mono для машинной эстетики. */
val KickerStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    letterSpacing = 1.8.sp,
)

/** Моноширинный для чисел/пингов/логов. */
val MonoStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
)
