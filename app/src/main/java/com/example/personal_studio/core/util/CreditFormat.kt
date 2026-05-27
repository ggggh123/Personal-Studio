package com.example.personal_studio.core.util

import java.util.Locale

/**
 * 学分显示格式化。正方常见学分含 0.25(形势与政策一类课),"%.1f" 会把 0.25
 * 错舍为 0.3。这里最多 2 位小数,末尾若是 0 去掉一位,保留至少 1 位小数:
 *   0.25 → "0.25"   1.5 → "1.5"   3.0 → "3.0"   2.0 → "2.0"
 */
object CreditFormat {
    fun format(credit: Double): String {
        val s = String.format(Locale.US, "%.2f", credit)
        return if (s.endsWith("0")) s.dropLast(1) else s
    }
}
