package com.example.personal_studio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.personal_studio.R

/**
 * 天光 type families. Headings and large numerals use a serif; body text uses the system
 * sans-serif (zero APK cost, best CJK coverage). Every style turns on tabular figures so
 * countdowns and scores never jitter where the font supports it.
 *
 * The serif is a bundled Noto Serif SC subset (SIL OFL 1.1, licence in
 * assets/licenses): every GB2312 character, printable ASCII, and the other characters
 * used in the app's sources. Lighter weights resolve to SemiBold.
 */
val SerifFamily: FontFamily = FontFamily(
    Font(R.font.noto_serif_sc_semibold, FontWeight.SemiBold),
    Font(R.font.noto_serif_sc_bold, FontWeight.Bold),
)
val SansFamily: FontFamily = FontFamily.Default

private const val TABULAR = "tnum"

private fun serif(weight: FontWeight, size: Int, line: Int) = TextStyle(
    fontFamily = SerifFamily, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    fontFeatureSettings = TABULAR,
)

private fun sans(weight: FontWeight, size: Int, line: Int) = TextStyle(
    fontFamily = SansFamily, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    fontFeatureSettings = TABULAR,
)

/** Material slots mapped onto the 天光 scale (display 34/40 … label 11/14; nothing below 11 sp). */
val TgTypography = Typography(
    displayLarge = serif(FontWeight.Bold, 64, 68),
    displayMedium = serif(FontWeight.SemiBold, 34, 40),
    displaySmall = serif(FontWeight.SemiBold, 28, 34),
    headlineLarge = serif(FontWeight.SemiBold, 24, 32),
    headlineMedium = serif(FontWeight.SemiBold, 21, 28),
    headlineSmall = serif(FontWeight.SemiBold, 18, 26),
    titleLarge = sans(FontWeight.Medium, 17, 24),
    titleMedium = sans(FontWeight.Medium, 15, 22),
    titleSmall = sans(FontWeight.Medium, 13, 18),
    bodyLarge = sans(FontWeight.Normal, 15, 22),
    bodyMedium = sans(FontWeight.Normal, 14, 21),
    bodySmall = sans(FontWeight.Normal, 12, 18),
    labelLarge = sans(FontWeight.Medium, 13, 18),
    labelMedium = sans(FontWeight.Medium, 12, 16),
    labelSmall = sans(FontWeight.Medium, 11, 14),
)
