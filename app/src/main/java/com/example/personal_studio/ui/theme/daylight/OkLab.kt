package com.example.personal_studio.ui.theme.daylight

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Perceptual colour helpers for the 天光 theme.
 *
 * Interpolating in OKLab keeps intermediate colours clean (sRGB mixing turns warm-to-cool
 * transitions muddy). Contrast follows WCAG 2.x relative luminance.
 */
object OkLab {

    data class Lab(val l: Float, val a: Float, val b: Float)

    fun toLab(color: Color): Lab {
        val r = linear(color.red)
        val g = linear(color.green)
        val bl = linear(color.blue)
        val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * bl)
        val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * bl)
        val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * bl)
        return Lab(
            l = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
            a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
            b = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s,
        )
    }

    fun fromLab(lab: Lab, alpha: Float = 1f): Color {
        val l = (lab.l + 0.3963377774f * lab.a + 0.2158037573f * lab.b).let { it * it * it }
        val m = (lab.l - 0.1055613458f * lab.a - 0.0638541728f * lab.b).let { it * it * it }
        val s = (lab.l - 0.0894841775f * lab.a - 1.2914855480f * lab.b).let { it * it * it }
        val r = gamma(4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s)
        val g = gamma(-1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s)
        val b = gamma(-0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s)
        return Color(r, g, b, alpha)
    }

    /** Interpolates two opaque colours in OKLab; [t] is clamped to 0..1. */
    fun mix(from: Color, to: Color, t: Float): Color {
        if (t <= 0f || from == to) return from
        if (t >= 1f) return to
        val a = toLab(from)
        val b = toLab(to)
        return quantize(
            fromLab(
                Lab(a.l + (b.l - a.l) * t, a.a + (b.a - a.a) * t, a.b + (b.b - a.b) * t),
                alpha = from.alpha + (to.alpha - from.alpha) * t,
            ),
        )
    }

    /** Euclidean distance in OKLab; ~0.02 is the threshold of a noticeable difference. */
    fun distance(x: Color, y: Color): Float {
        val a = toLab(x)
        val b = toLab(y)
        val dl = a.l - b.l
        val da = a.a - b.a
        val db = a.b - b.b
        return sqrt(dl * dl + da * da + db * db)
    }

    fun relativeLuminance(color: Color): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    /** WCAG contrast ratio of two opaque colours, 1.0 .. 21.0. */
    fun contrast(x: Color, y: Color): Double {
        val a = relativeLuminance(x)
        val b = relativeLuminance(y)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun linear(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

    private fun gamma(c: Float): Float {
        val v = if (abs(c) <= 0.0031308f) 12.92f * c else 1.055f * max(c, 0f).toDouble().pow(1 / 2.4).toFloat() - 0.055f
        return v.coerceIn(0f, 1f)
    }

    /** Snap to 8-bit channels so equal inputs always yield identical, comparable colours. */
    private fun quantize(c: Color): Color = Color(
        red = (c.red * 255f).roundToInt(),
        green = (c.green * 255f).roundToInt(),
        blue = (c.blue * 255f).roundToInt(),
        alpha = (c.alpha * 255f).roundToInt(),
    )
}
