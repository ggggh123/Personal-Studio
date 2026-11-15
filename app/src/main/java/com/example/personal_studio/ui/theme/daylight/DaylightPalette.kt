package com.example.personal_studio.ui.theme.daylight

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class ThemeFamily { LIGHT, DARK }

/** How the app chooses its palette. Stored in preferences; [DAYLIGHT] is the default. */
enum class ThemeMode {
    /** Follow the real sun: light/dark family and tint both come from the time of day. */
    DAYLIGHT,

    /** The system decides light or dark; the time of day only tints within that family. */
    SYSTEM,

    /** Always the noon palette. */
    LIGHT,

    /** Always the deep-night palette. */
    DARK,
}

/**
 * Semantic colour tokens of the 天光 theme. Every keyframe defines the full set.
 * `textTertiary` is for decoration and tracks only — never for text.
 */
@Immutable
data class TgColors(
    val family: ThemeFamily,
    val bg: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val line: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val error: Color,
) {
    val isLight: Boolean get() = family == ThemeFamily.LIGHT

    /** Dims content behind sheets and dialogs. */
    val scrim: Color get() = if (isLight) Color(0x4D1E160E) else Color(0x8F000000)

    fun lerp(to: TgColors, t: Float): TgColors = TgColors(
        family = if (t < 0.5f) family else to.family,
        bg = OkLab.mix(bg, to.bg, t),
        surface = OkLab.mix(surface, to.surface, t),
        surfaceRaised = OkLab.mix(surfaceRaised, to.surfaceRaised, t),
        text = OkLab.mix(text, to.text, t),
        textSecondary = OkLab.mix(textSecondary, to.textSecondary, t),
        textTertiary = OkLab.mix(textTertiary, to.textTertiary, t),
        line = OkLab.mix(line, to.line, t),
        accent = OkLab.mix(accent, to.accent, t),
        accentSoft = OkLab.mix(accentSoft, to.accentSoft, t),
        onAccent = OkLab.mix(onAccent, to.onAccent, t),
        error = OkLab.mix(error, to.error, t),
    )
}

/**
 * The eight keyframes of the day and the rules that interpolate between them.
 * Values and anchors match the design canvas ("天光设计系统 v0.1").
 */
object DaylightPalette {

    private val LIGHT_ERROR = Color(0xFF8C1D2E)
    private val DARK_ERROR = Color(0xFFF2808F)

    val Dawn = light(0xFFECEEF1, 0xFFF7F8FA, 0xFFFFFFFF, 0xFF1B1F27, 0xFF5B616D, 0xFFA0A6B0, 0xFFD9DDE3, 0xFF9C473C, 0xFFF2E3E0)
    val Morning = light(0xFFF5F3EE, 0xFFFBFAF7, 0xFFFFFFFF, 0xFF1C1B19, 0xFF635E56, 0xFFA8A197, 0xFFE2DDD3, 0xFFAE3F27, 0xFFF6E3DC)
    val Noon = light(0xFFF8F7F3, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF141311, 0xFF5F5B54, 0xFFA6A096, 0xFFE5E1D9, 0xFFB03A22, 0xFFF7E2DA)
    val Afternoon = light(0xFFF5EFE4, 0xFFFCF8F1, 0xFFFFFDF9, 0xFF1E1913, 0xFF665C4E, 0xFFAA9E8C, 0xFFE5DBCA, 0xFF9C4A1C, 0xFFF4E1D0)
    val Sunset = light(0xFFF2E5D6, 0xFFFAF2E8, 0xFFFDF8F2, 0xFF22180F, 0xFF6A5644, 0xFFAA947D, 0xFFE3D0BB, 0xFF9A3D20, 0xFFF1DACA)
    val Dusk = dark(0xFF1D1613, 0xFF271E1A, 0xFF2F2520, 0xFFF3E6DA, 0xFFB39C8A, 0xFF6E5D52, 0xFF3A2D27, 0xFFEB9466, 0xFF3E2A21)
    val Night = dark(0xFF13100C, 0xFF1C1814, 0xFF241F1A, 0xFFEFE6D8, 0xFFA99D8A, 0xFF635849, 0xFF322B23, 0xFFE3A54E, 0xFF33291B)
    val PreDawn = dark(0xFF171920, 0xFF1F222B, 0xFF272B35, 0xFFE7E9EF, 0xFF9EA3B1, 0xFF5B606C, 0xFF2E323C, 0xFFD2A9BE, 0xFF352B36)

    val keyframes: List<Pair<String, TgColors>> = listOf(
        "拂晓" to Dawn, "上午" to Morning, "正午" to Noon, "午后" to Afternoon,
        "日落" to Sunset, "入夜" to Dusk, "深夜" to Night, "黎明前" to PreDawn,
    )

    /** The family the sun itself implies at [hour] (hours after local midnight). */
    fun naturalFamily(hour: Double, sun: SunDay): ThemeFamily =
        if (sun.isLightHour(hour)) ThemeFamily.LIGHT else ThemeFamily.DARK

    /**
     * Palette of [family] at [hour]. When the family is held outside its natural hours
     * (a pending flip, or the system decides the family), it clamps to the nearest edge
     * of its own range: light at night shows the sunset or dawn end, dark by day shows
     * the dusk or pre-dawn end.
     */
    fun at(hour: Double, sun: SunDay, family: ThemeFamily = naturalFamily(hour, sun)): TgColors {
        val h = ((hour % 24) + 24) % 24
        return when (family) {
            ThemeFamily.LIGHT -> {
                val keys = listOf(
                    sun.dawnFlip to Dawn, sun.sunrise + 0.6 to Dawn, sun.sunrise + 2.5 to Morning,
                    sun.solarNoon to Noon, sun.sunset - 2.0 to Afternoon, sun.sunset - 0.3 to Sunset,
                    sun.duskFlip to Sunset,
                )
                val x = when {
                    sun.isLightHour(h) -> h
                    h >= sun.duskFlip -> sun.duskFlip
                    else -> sun.dawnFlip
                }
                interpolate(keys, x)
            }
            ThemeFamily.DARK -> {
                val keys = listOf(
                    sun.duskFlip to Dusk, sun.sunset + 1.0 to Dusk, sun.sunset + 2.6 to Night,
                    sun.sunrise + 22.6 to Night, sun.sunrise + 23.4 to PreDawn, sun.dawnFlip + 24 to PreDawn,
                )
                val x = when {
                    !sun.isLightHour(h) -> if (h < sun.dawnFlip) h + 24 else h
                    h < sun.solarNoon -> sun.dawnFlip + 24
                    else -> sun.duskFlip
                }
                interpolate(keys, x)
            }
        }
    }

    /** Palette for [mode]; [systemDark] is only consulted in [ThemeMode.SYSTEM]. */
    fun resolve(mode: ThemeMode, hour: Double, sun: SunDay, systemDark: Boolean, heldFamily: ThemeFamily?): TgColors =
        when (mode) {
            ThemeMode.DAYLIGHT -> at(hour, sun, heldFamily ?: naturalFamily(hour, sun))
            ThemeMode.SYSTEM -> at(hour, sun, if (systemDark) ThemeFamily.DARK else ThemeFamily.LIGHT)
            ThemeMode.LIGHT -> Noon
            ThemeMode.DARK -> Night
        }

    private fun interpolate(keys: List<Pair<Double, TgColors>>, x: Double): TgColors {
        for (i in 0 until keys.lastIndex) {
            val (a, ca) = keys[i]
            val (b, cb) = keys[i + 1]
            if (x >= a && x <= b) {
                val t = if (b == a) 0.0 else (x - a) / (b - a)
                return ca.lerp(cb, smoothstep(t).toFloat())
            }
        }
        return if (x < keys.first().first) keys.first().second else keys.last().second
    }

    private fun smoothstep(t: Double): Double {
        val c = t.coerceIn(0.0, 1.0)
        return c * c * (3 - 2 * c)
    }

    private fun light(
        bg: Long, surface: Long, raised: Long, text: Long, text2: Long, text3: Long,
        line: Long, accent: Long, accentSoft: Long,
    ) = TgColors(
        ThemeFamily.LIGHT, Color(bg), Color(surface), Color(raised), Color(text), Color(text2),
        Color(text3), Color(line), Color(accent), Color(accentSoft), Color(0xFFFFFFFF), LIGHT_ERROR,
    )

    private fun dark(
        bg: Long, surface: Long, raised: Long, text: Long, text2: Long, text3: Long,
        line: Long, accent: Long, accentSoft: Long,
    ) = TgColors(
        ThemeFamily.DARK, Color(bg), Color(surface), Color(raised), Color(text), Color(text2),
        Color(text3), Color(line), Color(accent), Color(accentSoft), Color(bg), DARK_ERROR,
    )
}

/**
 * Course colours stay fixed through the day (recognition beats ambience); each has a
 * light-family and a dark-family pair of block background and foreground.
 */
object CoursePalette {
    data class Swatch(val container: Color, val content: Color)

    private val LIGHT = listOf(
        Swatch(Color(0xFFE4E8F4), Color(0xFF2C3D78)), Swatch(Color(0xFFE2ECDD), Color(0xFF33532A)),
        Swatch(Color(0xFFF2E6D1), Color(0xFF6A4615)), Swatch(Color(0xFFDAECEA), Color(0xFF1D5551)),
        Swatch(Color(0xFFECE2EE), Color(0xFF583261)), Swatch(Color(0xFFF3DFDA), Color(0xFF77301F)),
    )
    private val DARK = listOf(
        Swatch(Color(0xFF262E48), Color(0xFFC9D3F3)), Swatch(Color(0xFF263624), Color(0xFFC6DCBE)),
        Swatch(Color(0xFF3A2E1B), Color(0xFFEBD2A8)), Swatch(Color(0xFF1C3634), Color(0xFFB3DDD8)),
        Swatch(Color(0xFF36263C), Color(0xFFDFC7E5)), Swatch(Color(0xFF40231E), Color(0xFFEFC3BA)),
    )

    val size: Int get() = LIGHT.size

    fun forKey(key: String, family: ThemeFamily): Swatch =
        (if (family == ThemeFamily.LIGHT) LIGHT else DARK)[Math.floorMod(key.hashCode(), LIGHT.size)]
}
