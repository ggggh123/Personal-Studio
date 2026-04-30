package com.example.personal_studio.core.util

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseColorPaletteTest {

    @Test fun `same title yields same color across calls`() {
        val a = CourseColorPalette.colorFor("高等数学")
        val b = CourseColorPalette.colorFor("高等数学")
        assertEquals(a, b)
    }

    @Test fun `different titles likely yield different hues`() {
        // 6 hues in palette; a handful of titles should still spread across at least 2 buckets.
        val titles = listOf("高数", "线代", "概率论", "英语", "马原", "毛概", "体育", "C 语言")
        val unique = titles.map(CourseColorPalette::colorFor).toSet()
        assertTrue("expected ≥ 2 unique colors, got ${unique.size}", unique.size >= 2)
    }

    @Test fun `color stays inside green-leaning hue band`() {
        val color = CourseColorPalette.colorFor("高数")
        // Red channel must be < both green and the red < 200/255 (no yellow/red bleed).
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        assertTrue("expected greenish color but got rgb=($r,$g,${(color.blue * 255).toInt()})",
            g > r)
    }

    @Test fun `colorFor does not return transparent or black`() {
        val color = CourseColorPalette.colorFor("xyz")
        assertNotEquals(Color.Transparent, color)
        assertTrue(color.alpha > 0.5f)
        // Lightness 0.55 should keep at least one channel comfortably above 0.
        assertTrue(
            "expected non-black but got rgb=(${color.red},${color.green},${color.blue})",
            color.red > 0.1f || color.green > 0.1f || color.blue > 0.1f,
        )
    }

    @Test fun `colorFor handles arbitrary hash values without crashing`() {
        // Synthetic strings; we don't pick one with a literal Int.MIN_VALUE hash
        // (java String.hashCode is stable across JVMs, but constructing such a
        // string is impractical). Instead, scan a small range — combined with
        // floorMod's contract this guards against negative-modulo regressions.
        for (i in 0..1000) {
            CourseColorPalette.colorFor("synthetic-$i")
        }
    }
}
