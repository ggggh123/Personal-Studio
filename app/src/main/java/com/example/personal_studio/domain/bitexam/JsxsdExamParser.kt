package com.example.personal_studio.domain.bitexam

import com.example.personal_studio.domain.bitexam.model.ExamItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 解析 BIT 正方(强智 jsxsd)考试安排页 HTML → [ExamItem]。
 * 表 `<table id="dataList">`,首行表头按名映射列(对列序鲁棒);考试时间 cell 形如
 * `2026-01-05 08:00~10:00`(分隔符 ~ / ～ / -)拆 startAt/endAt(+08:00)。
 * 克隆 JsxsdGradeParser 的正则风格(无 jsoup)。表 id / 列名待真机确认。
 */
class JsxsdExamParser @Inject constructor() {

    fun parse(html: String, term: String): List<ExamItem> {
        val table = TABLE.find(html)?.groupValues?.get(1) ?: return emptyList()
        val rows = ROW.findAll(table).map { it.groupValues[1] }.toList()
        if (rows.size < 2) return emptyList()

        val headers = CELL.findAll(rows[0]).map { clean(it.groupValues[1]) }.toList()
        fun col(vararg keys: String): Int = headers.indexOfFirst { h -> keys.any { it in h } }
        val ciName = col("课程名")
        val ciCode = col("课程编号", "课程号")
        val ciTime = col("考试时间", "考试日期")
        val ciPlace = col("考点", "考试地点", "地点")
        val ciSeat = col("座位")
        val ciTeacher = col("监考", "任课教师", "教师")

        return rows.drop(1).mapNotNull { r ->
            val cells = CELL.findAll(r).map { clean(it.groupValues[1]) }.toList()
            fun at(i: Int): String = if (i in cells.indices) cells[i] else ""
            val name = at(ciName).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val (start, end) = parseTime(at(ciTime)) ?: return@mapNotNull null
            val code = at(ciCode)
            ExamItem(
                uid = "$term|${code.ifBlank { name }}|$start",
                course = name,
                startAt = start,
                endAt = end,
                location = at(ciPlace).ifBlank { null },
                seat = at(ciSeat).ifBlank { null },
                invigilator = at(ciTeacher).ifBlank { null },
            )
        }
    }

    /** 拆 `2026-01-05 08:00~10:00` → (start,end);只日期 → (当天00:00, null);拆不出 → null。 */
    private fun parseTime(cell: String): Pair<Long, Long?>? = runCatching {
        val m = RANGE.find(cell)
        if (m != null) {
            val date = LocalDate.parse(m.groupValues[1], DATE)
            val s = LocalTime.parse(pad(m.groupValues[2]), TIME)
            val e = LocalTime.parse(pad(m.groupValues[3]), TIME)
            LocalDateTime.of(date, s).toInstant(ZoneOffset.ofHours(8)).toEpochMilli() to
                LocalDateTime.of(date, e).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
        } else {
            val d = DATE_ONLY.find(cell)?.groupValues?.get(1) ?: return null
            LocalDate.parse(d, DATE).atStartOfDay(ZoneOffset.ofHours(8)).toInstant().toEpochMilli() to null
        }
    }.getOrNull()

    /** `8:00` → `08:00`,供 HH:mm 解析。 */
    private fun pad(t: String): String = if (t.length == 4) "0$t" else t

    private fun clean(cell: String): String = cell
        .replace(TAG, "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace(WS, " ").trim()

    companion object {
        private val TABLE = Regex("""<table[^>]*\bid=["']dataList["'][^>]*>(.*?)</table>""", RegexOption.DOT_MATCHES_ALL)
        private val ROW = Regex("""<tr[^>]*>(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
        private val CELL = Regex("""<t[dh][^>]*>(.*?)</t[dh]>""", RegexOption.DOT_MATCHES_ALL)
        private val TAG = Regex("""<[^>]*>""")
        private val WS = Regex("""\s+""")
        private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val TIME = DateTimeFormatter.ofPattern("HH:mm")
        private val RANGE = Regex("""(\d{4}-\d{1,2}-\d{1,2})\s+(\d{1,2}:\d{2})\s*[~～\-－]\s*(\d{1,2}:\d{2})""")
        private val DATE_ONLY = Regex("""(\d{4}-\d{1,2}-\d{1,2})""")
        private val XNXQID_SELECTED = Regex("""<option[^>]*value=["']([^"']+)["'][^>]*\bselected\b""")
        private val XNXQID_FIRST = Regex("""<select[^>]*\bid=["']xnxqid["'][^>]*>.*?<option[^>]*value=["']([^"']+)["']""", RegexOption.DOT_MATCHES_ALL)

        /** 从 xsksap_query 页抠当前学期 xnxqid:优先 selected option,否则 xnxqid select 的首个 option。 */
        fun extractCurrentTerm(html: String): String? =
            XNXQID_SELECTED.find(html)?.groupValues?.get(1)
                ?: XNXQID_FIRST.find(html)?.groupValues?.get(1)
    }
}
