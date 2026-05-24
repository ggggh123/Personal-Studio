package com.example.personal_studio.core.util

import com.example.personal_studio.domain.bitgrades.model.GradeItem
import org.junit.Assert.assertEquals
import org.junit.Test

class GradeBucketerTest {
    private fun item(score: String, point: Double? = null) =
        GradeItem("c", "c", 3.0, score, point, null, null, "正常", true)

    @Test fun `numeric scores bucket into A-F bands`() {
        val buckets = GradeBucketer.bucket(listOf(
            item("92"), item("85"), item("85"), item("73"), item("61"), item("40"),
        ))
        // 顺序固定 A,B,C,D,F；只保留 count>0
        assertEquals(listOf("A" to 1, "B" to 2, "C" to 1, "D" to 1, "F" to 1),
            buckets.map { it.label to it.count })
    }

    @Test fun `non-numeric scores bucket by raw label`() {
        val buckets = GradeBucketer.bucket(listOf(item("优"), item("优"), item("良")))
        assertEquals(setOf("优" to 2, "良" to 1), buckets.map { it.label to it.count }.toSet())
    }
}
