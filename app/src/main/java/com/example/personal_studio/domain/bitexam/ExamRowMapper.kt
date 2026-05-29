package com.example.personal_studio.domain.bitexam

import com.example.personal_studio.data.network.bit.dto.ExamRowDto
import com.example.personal_studio.domain.bitexam.model.ExamItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** 把 ehall studentWdksapApp cxxsksap 的一行映射成 ExamItem。
 *  KCM 取方括号内中文名;KSSJMS `yyyy-MM-dd HH:mm-HH:mm(星期X)` 拆起止(+08:00)。 */
class ExamRowMapper @Inject constructor() {
    fun invoke(row: ExamRowDto, term: String): ExamItem? {
        val (start, end) = parseTime(row.kssjms) ?: parseDateOnly(row.ksrq) ?: return null
        val course = row.kcm?.let { cn(it) }?.takeIf { it.isNotBlank() } ?: return null
        val kch = row.kch ?: course
        return ExamItem(
            uid = "$term|$kch|${row.ksrq ?: start}",
            course = course,
            startAt = start,
            endAt = end,
            location = row.jasmc?.ifBlank { null },
            seat = row.zwh?.ifBlank { null },
            invigilator = row.zjjsxm?.ifBlank { null },
        )
    }

    /** "100074340[人工智能概论]" → "人工智能概论";无方括号则原值。 */
    private fun cn(kcm: String): String =
        if ('[' in kcm && ']' in kcm) kcm.substringAfter('[').substringBefore(']') else kcm

    /** "2026-07-01 10:10-12:10(星期三)" → (start,end) +08:00。 */
    private fun parseTime(s: String?): Pair<Long, Long?>? {
        if (s == null) return null
        val m = RANGE.find(s) ?: return null
        return runCatching {
            val d = LocalDate.parse(m.groupValues[1], DATE)
            val st = LocalTime.parse(pad(m.groupValues[2]), TIME)
            val en = LocalTime.parse(pad(m.groupValues[3]), TIME)
            LocalDateTime.of(d, st).toInstant(ZoneOffset.ofHours(8)).toEpochMilli() to
                LocalDateTime.of(d, en).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
        }.getOrNull()
    }

    private fun parseDateOnly(ksrq: String?): Pair<Long, Long?>? {
        if (ksrq == null) return null
        val d = DATE_ONLY.find(ksrq)?.groupValues?.get(1) ?: return null
        return runCatching {
            LocalDate.parse(d, DATE).atStartOfDay(ZoneOffset.ofHours(8)).toInstant().toEpochMilli() to null
        }.getOrNull()
    }

    private fun pad(t: String) = if (t.length == 4) "0$t" else t

    companion object {
        private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val TIME = DateTimeFormatter.ofPattern("HH:mm")
        private val RANGE = Regex("""(\d{4}-\d{1,2}-\d{1,2})\s+(\d{1,2}:\d{2})\s*[-~～－]\s*(\d{1,2}:\d{2})""")
        private val DATE_ONLY = Regex("""(\d{4}-\d{1,2}-\d{1,2})""")
    }
}
