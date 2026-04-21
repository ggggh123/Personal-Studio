package com.example.personal_studio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.personal_studio.R

// Bundled fonts in res/font/.
//
// Maple Mono CN is a JetBrains Mono-derived typeface that adds full CJK coverage
// (20k+ glyphs) in matching monospace proportions. Using it as the single UI family
// gives us consistent rendering for mixed Latin + Chinese content without CJK
// fallback seams. Cost: +54 MB to the APK for 3 weights (Regular, SemiBold, Bold).
// Tradeoff explicitly accepted per project preferences (no Play Services, no
// first-launch font downloads on Chinese OEMs).

val MapleMonoCnFamily = FontFamily(
    Font(R.font.maple_mono_cn_regular, FontWeight.Normal),
    Font(R.font.maple_mono_cn_regular, FontWeight.Medium),    // synthesize from Regular
    Font(R.font.maple_mono_cn_semibold, FontWeight.SemiBold),
    Font(R.font.maple_mono_cn_bold, FontWeight.Bold),
)

val Vt323Family = FontFamily(
    Font(R.font.vt323, FontWeight.Normal),
)

val FrauncesFamily = FontFamily(
    Font(R.font.fraunces_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.fraunces_italic, FontWeight.Medium, FontStyle.Italic),
)

// Canonical UI family for everything except hero display titles and math formulas.
val PrimaryFamily = MapleMonoCnFamily

val TerminalTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Vt323Family, fontWeight = FontWeight.Normal,
        fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = 0.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Vt323Family, fontWeight = FontWeight.Normal,
        fontSize = 32.sp, lineHeight = 36.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = PrimaryFamily, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em,
    ),
    headlineLarge = TextStyle(
        fontFamily = PrimaryFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = PrimaryFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = PrimaryFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = PrimaryFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = PrimaryFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = PrimaryFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.em,
    ),
    bodyLarge = TextStyle(
        fontFamily = PrimaryFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = PrimaryFamily, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = PrimaryFamily, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = PrimaryFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.15.em,
    ),
    labelMedium = TextStyle(
        fontFamily = PrimaryFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.18.em,
    ),
    labelSmall = TextStyle(
        fontFamily = PrimaryFamily, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.2.em,
    ),
)
