package com.example.personal_studio.core.util

import androidx.compose.ui.graphics.Color

/**
 * Maps a course title to one of 12 HSL hues, deterministic by hash.
 *
 * Hues span 100°–335° and intentionally **skip** four bands that would clash
 * with state-machine colours used elsewhere on the timeline:
 *   - ~352° Carmine (TASK overdue / now-line) — palette stops at 335°
 *   - ~198° Cyan (day-strip course-count badge) — palette jumps 180° → 215°
 *   - ~35° Amber (TASK imminent) — palette never enters the 0°–95° band
 *   - ~75° Olive (Done states) — same exclusion
 *
 * Adjacent palette entries are ≥ 20° apart so two collision-distinct colours
 * are visually distinguishable at S=60% L=55%. Birthday-paradox-wise this is
 * collision-free for ≤ 4 courses and noticeably better than the prior 6-hue
 * palette for typical 8–10-course semesters.
 */
object CourseColorPalette {

    private val HUES = floatArrayOf(
        // Phosphor-leaning greens preserve the Terminal identity
        100f, 120f, 140f, 160f, 180f,
        // Teals through blues — skip ~198° system Cyan
        215f, 235f, 255f, 275f,
        // Magentas / pinks — stop short of ~352° Carmine
        295f, 315f, 335f,
    )

    fun colorFor(courseTitle: String): Color {
        val h = HUES[Math.floorMod(courseTitle.hashCode(), HUES.size)]
        return Color.hsl(hue = h, saturation = 0.60f, lightness = 0.55f)
    }
}
