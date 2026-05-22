package com.example.personal_studio.core.util

/**
 * Expands BIT 教务's 上课周次 bitmap string ("SKZC") into a list of 1-based week
 * indices.
 *
 * Example: "11011" → [1, 2, 4, 5]
 *
 * The BIT API returns one character per semester week ('1' = this week the
 * course meets, '0' = no class). Typical strings are 16-20 chars; we cap at 30
 * to catch malformed inputs cheaply.
 */
object SkzcExpander {

    private const val MAX_WEEKS = 30

    fun expand(skzc: String): List<Int> {
        require(skzc.length <= MAX_WEEKS) {
            "SKZC length ${skzc.length} exceeds MAX_WEEKS=$MAX_WEEKS"
        }
        return skzc.mapIndexedNotNull { i, c ->
            when (c) {
                '1' -> i + 1
                '0' -> null
                else -> throw IllegalArgumentException(
                    "SKZC contains non-binary character '$c' at index $i"
                )
            }
        }
    }
}
