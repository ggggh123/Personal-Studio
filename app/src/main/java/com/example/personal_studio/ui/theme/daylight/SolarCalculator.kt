package com.example.personal_studio.ui.theme.daylight

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sun times for one local calendar day, as hours after local midnight (may be fractional).
 * [sunrise]/[sunset] use the standard −0.833° altitude (refraction + solar radius).
 */
data class SunDay(
    val date: LocalDate,
    val sunrise: Double,
    val sunset: Double,
    val solarNoon: Double,
    val maxElevation: Double,
) {
    val dawnFlip: Double get() = sunrise - DAWN_FLIP_BEFORE_SUNRISE_H
    val duskFlip: Double get() = sunset + DUSK_FLIP_AFTER_SUNSET_H

    /** Whether the light family is natural at [hour] (between the two flips). */
    fun isLightHour(hour: Double): Boolean = hour >= dawnFlip && hour < duskFlip

    companion object {
        const val DAWN_FLIP_BEFORE_SUNRISE_H = 0.2
        const val DUSK_FLIP_AFTER_SUNSET_H = 0.45
    }
}

/**
 * Offline solar position for a fixed observer. Defaults to 北京理工大学（中关村校区）;
 * the app never asks for location permission. Accuracy is ~1 minute for sunrise/sunset,
 * which is all the theme needs.
 */
class SolarCalculator(
    private val latitude: Double = BIT_LATITUDE,
    private val longitude: Double = BIT_LONGITUDE,
    val zone: ZoneId = BEIJING,
) {
    private val cache = ConcurrentHashMap<LocalDate, SunDay>()

    /** Solar elevation in degrees at [instant]. */
    fun elevation(instant: Instant): Double {
        val d = instant.toEpochMilli() / 86_400_000.0 - J2000_EPOCH_DAYS
        val g = Math.toRadians(357.529 + 0.98560028 * d)
        val q = 280.459 + 0.98564736 * d
        val l = Math.toRadians(q + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val e = Math.toRadians(23.439 - 0.00000036 * d)
        val ra = atan2(cos(e) * sin(l), cos(l))
        val dec = asin(sin(e) * sin(l))
        val gmst = ((18.697374558 + 24.06570982441908 * d) % 24 + 24) % 24
        val ha = Math.toRadians(gmst * 15 + longitude) - ra
        val lat = Math.toRadians(latitude)
        return Math.toDegrees(asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(ha)))
    }

    /** Elevation at [hour] hours after local midnight of [date]. */
    fun elevationAt(date: LocalDate, hour: Double): Double =
        elevation(midnight(date).plusMillis((hour * 3_600_000).toLong()))

    fun sunDay(date: LocalDate): SunDay = cache.getOrPut(date) { compute(date) }

    private fun compute(date: LocalDate): SunDay {
        val step = 0.05
        var prev = elevationAt(date, 0.0)
        var rise = 6.0
        var set = 18.0
        var noon = 12.0
        var maxE = -90.0
        var h = step
        while (h <= 24.0 + 1e-9) {
            val e = elevationAt(date, h)
            if (prev < HORIZON && e >= HORIZON) rise = h - step + step * (HORIZON - prev) / (e - prev)
            if (prev >= HORIZON && e < HORIZON) set = h - step + step * (prev - HORIZON) / (prev - e)
            if (e > maxE) { maxE = e; noon = h }
            prev = e
            h += step
        }
        return SunDay(date, rise, set, noon, maxE)
    }

    private fun midnight(date: LocalDate): Instant = date.atStartOfDay(zone).toInstant()

    companion object {
        const val BIT_LATITUDE = 39.96
        const val BIT_LONGITUDE = 116.31
        val BEIJING: ZoneId = ZoneId.of("Asia/Shanghai")
        private const val HORIZON = -0.833
        private const val J2000_EPOCH_DAYS = 10_957.5
    }
}

/** Lunar phase from a mean synodic month; good to well under a day, enough for an icon. */
object MoonPhase {
    private const val SYNODIC_DAYS = 29.530588853
    private val REFERENCE_NEW_MOON: Instant = Instant.parse("2000-01-06T18:14:00Z")

    /** 0 = new, 0.25 = first quarter, 0.5 = full, 0.75 = last quarter. */
    fun fraction(instant: Instant): Double {
        val days = (instant.toEpochMilli() - REFERENCE_NEW_MOON.toEpochMilli()) / 86_400_000.0
        val age = ((days % SYNODIC_DAYS) + SYNODIC_DAYS) % SYNODIC_DAYS
        return age / SYNODIC_DAYS
    }

    fun name(fraction: Double): String = when {
        fraction < 0.034 || fraction > 0.966 -> "新月"
        fraction < 0.22 -> "蛾眉月"
        fraction < 0.28 -> "上弦月"
        fraction < 0.466 -> "盈凸月"
        fraction < 0.534 -> "满月"
        fraction < 0.72 -> "亏凸月"
        fraction < 0.78 -> "下弦月"
        else -> "残月"
    }
}
