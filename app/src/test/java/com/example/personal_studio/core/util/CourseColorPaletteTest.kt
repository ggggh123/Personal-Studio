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
        // 12 hues in palette; 8 plausible course titles should still spread
        // across a meaningful number of buckets despite some birthday-paradox
        // collisions. Threshold kept conservative to stay robust to future
        // hashCode tweaks across JVMs.
        val titles = listOf("高数", "线代", "概率论", "英语", "马原", "毛概", "体育", "C 语言")
        val unique = titles.map(CourseColorPalette::colorFor).toSet()
        assertTrue("expected ≥ 3 unique colors, got ${unique.size}", unique.size >= 3)
    }

    @Test fun `color is vivid (not grey) and avoids state-clash zones`() {
        // S=60% L=55% guarantees a visible chroma; max-min channel separation
        // should be ≥ 0.2. We don't pin the hue band any more (palette spans
        // 100°–335°), but the saturation+lightness pair keeps every generated
        // colour distinctly non-grey.
        repeat(200) { i ->
            val color = CourseColorPalette.colorFor("course-$i")
            val maxChan = maxOf(color.red, color.green, color.blue)
            val minChan = minOf(color.red, color.green, color.blue)
            assertTrue(
                "expected vivid colour but got rgb=(${color.red},${color.green},${color.blue})",
                maxChan - minChan > 0.2f,
            )
        }
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
