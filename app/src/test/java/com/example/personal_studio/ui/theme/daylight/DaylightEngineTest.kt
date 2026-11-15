package com.example.personal_studio.ui.theme.daylight

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min

class OkLabTest {
    @Test
    fun `round trip keeps 8-bit colours`() {
        listOf(0xFFB03A22, 0xFF13100C, 0xFFF8F7F3, 0xFF2C3D78, 0xFFE3A54E).forEach { argb ->
            val c = Color(argb)
            val back = OkLab.fromLab(OkLab.toLab(c))
            assertEquals(c.red, back.red, 1.5f / 255f)
            assertEquals(c.green, back.green, 1.5f / 255f)
            assertEquals(c.blue, back.blue, 1.5f / 255f)
        }
    }

    @Test
    fun `mix returns endpoints and contrast spans 1 to 21`() {
        val a = Color(0xFFF8F7F3)
        val b = Color(0xFF13100C)
        assertEquals(a, OkLab.mix(a, b, 0f))
        assertEquals(b, OkLab.mix(a, b, 1f))
        assertEquals(21.0, OkLab.contrast(Color.White, Color.Black), 0.01)
        assertEquals(1.0, OkLab.contrast(a, a), 0.0001)
    }
}

class SolarCalculatorTest {
    private val solar = SolarCalculator()

    private fun assertMinutes(expectedHour: Int, expectedMinute: Int, actualHours: Double, toleranceMin: Double = 4.0) {
        val expected = expectedHour * 60 + expectedMinute
        val actual = actualHours * 60
        assertTrue("expected ${expectedHour}:${expectedMinute}, was ${actual / 60}", abs(actual - expected) <= toleranceMin)
    }

    @Test
    fun `beijing solstice sunrise and sunset match published tables`() {
        val summer = solar.sunDay(LocalDate.of(2026, 6, 21))
        assertMinutes(4, 46, summer.sunrise)
        assertMinutes(19, 46, summer.sunset)
        val winter = solar.sunDay(LocalDate.of(2026, 12, 21))
        assertMinutes(7, 33, winter.sunrise)
        assertMinutes(16, 53, winter.sunset)
    }

    @Test
    fun `noon elevation follows latitude and declination`() {
        // 90° − 39.96° ± 23.44°
        assertEquals(73.5, solar.sunDay(LocalDate.of(2026, 6, 21)).maxElevation, 0.7)
        assertEquals(26.6, solar.sunDay(LocalDate.of(2026, 12, 21)).maxElevation, 0.7)
    }

    @Test
    fun `moon phase hits known new and full moons`() {
        // Total solar eclipse (new moon) and the following full moon.
        val newMoon = MoonPhase.fraction(Instant.parse("2024-04-08T18:21:00Z"))
        assertTrue("new moon fraction $newMoon", min(newMoon, 1 - newMoon) < 0.04)
        val fullMoon = MoonPhase.fraction(Instant.parse("2024-04-23T23:49:00Z"))
        assertEquals(0.5, fullMoon, 0.04)
        assertEquals("满月", MoonPhase.name(fullMoon))
    }
}

class DaylightPaletteTest {
    private val solar = SolarCalculator()

    private fun assertReadable(label: String, c: TgColors) {
        val surfaces = listOf(c.bg, c.surface, c.surfaceRaised)
        fun check(name: String, fg: Color, bgs: List<Color>) = bgs.forEach { bg ->
            val ratio = OkLab.contrast(fg, bg)
            assertTrue("$label: $name contrast $ratio < 4.5", ratio >= 4.5)
        }
        check("text", c.text, surfaces)
        check("textSecondary", c.textSecondary, surfaces)
        check("accent", c.accent, surfaces)
        check("accent on accentSoft", c.accent, listOf(c.accentSoft))
        check("onAccent", c.onAccent, listOf(c.accent))
        check("error", c.error, surfaces)
    }

    @Test
    fun `every keyframe meets WCAG AA`() {
        DaylightPalette.keyframes.forEach { (name, c) -> assertReadable(name, c) }
    }

    @Test
    fun `a whole year sampled every 10 minutes stays readable`() {
        var day = LocalDate.of(2026, 1, 1)
        while (day.year == 2026) {
            val sun = solar.sunDay(day)
            for (step in 0 until 144) {
                val hour = step / 6.0
                assertReadable("$day ${"%.2f".format(hour)}", DaylightPalette.at(hour, sun))
            }
            day = day.plusDays(1)
        }
    }

    @Test
    fun `family flips exactly twice a day and drifts smoothly in between`() {
        listOf(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 6, 21), LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 21)).forEach { day ->
            val sun = solar.sunDay(day)
            var flips = 0
            var prev = DaylightPalette.at(0.0, sun)
            for (step in 1..288) {
                val next = DaylightPalette.at(step / 12.0, sun)
                if (next.family != prev.family) {
                    flips++
                } else {
                    val d = OkLab.distance(prev.bg, next.bg)
                    assertTrue("$day ${step / 12.0}: background jumped by $d", d < 0.03f)
                }
                prev = next
            }
            assertEquals("$day flips", 2, flips)
        }
    }

    @Test
    fun `held family clamps to the nearest edge of its own range`() {
        val sun = solar.sunDay(LocalDate.of(2026, 11, 15))
        assertEquals(DaylightPalette.Sunset, DaylightPalette.at(sun.duskFlip + 1, sun, ThemeFamily.LIGHT))
        assertEquals(DaylightPalette.Dawn, DaylightPalette.at(sun.dawnFlip - 1, sun, ThemeFamily.LIGHT))
        assertEquals(DaylightPalette.PreDawn, DaylightPalette.at(sun.solarNoon - 2, sun, ThemeFamily.DARK))
        assertEquals(DaylightPalette.Dusk, DaylightPalette.at(sun.solarNoon + 2, sun, ThemeFamily.DARK))
    }

    @Test
    fun `modes resolve as designed`() {
        val sun = solar.sunDay(LocalDate.of(2026, 11, 15))
        assertEquals(DaylightPalette.Noon, DaylightPalette.resolve(ThemeMode.LIGHT, 23.0, sun, systemDark = true, heldFamily = null))
        assertEquals(DaylightPalette.Night, DaylightPalette.resolve(ThemeMode.DARK, 12.0, sun, systemDark = false, heldFamily = null))
        assertEquals(ThemeFamily.DARK, DaylightPalette.resolve(ThemeMode.SYSTEM, 12.0, sun, systemDark = true, heldFamily = null).family)
        assertEquals(ThemeFamily.LIGHT, DaylightPalette.resolve(ThemeMode.DAYLIGHT, 23.0, sun, systemDark = true, heldFamily = ThemeFamily.LIGHT).family)
        assertEquals(ThemeFamily.DARK, DaylightPalette.resolve(ThemeMode.DAYLIGHT, 23.0, sun, systemDark = false, heldFamily = null).family)
    }
}

class FamilyGateTest {
    @Test
    fun `first proposal applies immediately`() {
        val gate = FamilyGate()
        assertEquals(ThemeFamily.LIGHT, gate.propose(ThemeFamily.LIGHT))
    }

    @Test
    fun `a change waits for a safe point`() {
        val gate = FamilyGate(ThemeFamily.LIGHT)
        assertEquals(ThemeFamily.LIGHT, gate.propose(ThemeFamily.DARK))
        assertTrue(gate.hasPending)
        assertTrue(gate.safePoint())
        assertEquals(ThemeFamily.DARK, gate.applied)
        assertEquals(false, gate.safePoint())
    }

    @Test
    fun `a change that reverts before the safe point is dropped`() {
        val gate = FamilyGate(ThemeFamily.LIGHT)
        gate.propose(ThemeFamily.DARK)
        gate.propose(ThemeFamily.LIGHT)
        assertEquals(false, gate.safePoint())
        assertEquals(ThemeFamily.LIGHT, gate.applied)
    }
}
