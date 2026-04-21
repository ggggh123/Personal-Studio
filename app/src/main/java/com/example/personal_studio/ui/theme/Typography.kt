package com.example.personal_studio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.personal_studio.R

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val jetbrainsMono = GoogleFont("JetBrains Mono")
private val vt323 = GoogleFont("VT323")
private val fraunces = GoogleFont("Fraunces")
private val notoSansSc = GoogleFont("Noto Sans SC")

// Used for UI / body / almost everything
val JetBrainsMonoFamily = FontFamily(
    Font(googleFont = jetbrainsMono, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = jetbrainsMono, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = jetbrainsMono, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = jetbrainsMono, fontProvider = googleFontProvider, weight = FontWeight.Bold),
)

// Used for display titles only (CRT-bitmap feel)
val Vt323Family = FontFamily(
    Font(googleFont = vt323, fontProvider = googleFontProvider, weight = FontWeight.Normal),
)

// Used for math / formulas — italic serif to break the mono discipline meaningfully
val FrauncesFamily = FontFamily(
    Font(googleFont = fraunces, fontProvider = googleFontProvider, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(googleFont = fraunces, fontProvider = googleFontProvider, weight = FontWeight.Medium, style = FontStyle.Italic),
)

// CJK fallback for body text
val NotoSansScFamily = FontFamily(
    Font(googleFont = notoSansSc, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = notoSansSc, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = notoSansSc, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
)

// Composite family that prefers JetBrains Mono for Latin and falls back to Noto Sans SC for CJK.
// Compose resolves glyphs left-to-right through the family list.
val PrimaryFamily = FontFamily(
    Font(googleFont = jetbrainsMono, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = jetbrainsMono, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = jetbrainsMono, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = jetbrainsMono, fontProvider = googleFontProvider, weight = FontWeight.Bold),
    Font(googleFont = notoSansSc, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = notoSansSc, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = notoSansSc, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
)

val TerminalTypography = Typography(
    // Display uses VT323 (CRT bitmap feel) for the largest titles
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
