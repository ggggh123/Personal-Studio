package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.core.util.BitGpaConverter
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import javax.inject.Inject

/**
 * Parses BIT 正方教务 (jsxsd) `cjcx_list` HTML into [GradeEntryEntity].
 *
 * The page has a `<table id="dataList">` whose first row is Chinese column
 * headers and the rest are grade rows (per BIT101-GO webvpn/score.go). We map
 * columns by matching header text (robust to column order/extra columns), so a
 * layout change only breaks if BIT renames a column outright.
 *
 * Regex-based (no jsoup) to stay dependency-free, matching the CAS-page parser
 * style. Exact header strings are confirmed against real-device HTML in DoD.
 */
class JsxsdGradeParser @Inject constructor() {

    /** True if the page is gated behind 评教 (course evaluation) instead of grades. */
    fun isReviewGated(html: String): Boolean =
        "评教" in html && "dataList" !in html

    fun parse(html: String, fetchedAt: Long): List<GradeEntryEntity> {
        val table = TABLE.find(html)?.groupValues?.get(1) ?: return emptyList()
        val rows = ROW.findAll(table).map { it.groupValues[1] }.toList()
        if (rows.size < 2) return emptyList()

        val headers = CELL.findAll(rows[0]).map { clean(it.groupValues[1]) }.toList()
        fun col(vararg keys: String): Int =
            headers.indexOfFirst { h -> keys.any { it in h } }

        // 列名按真机 jsxsd 表头匹配（序号/开课学期/课程编号/课程名称/成绩/成绩标识/学分/
        // 总学时/考试性质/.../课程性质/...）。正方不给"绩点"列，绩点由成绩本地换算。
        val ciTerm = col("开课学期", "学年学期", "学期")
        val ciName = col("课程名")
        val ciCode = col("课程编号", "课程号")
        val ciCredit = col("学分")
        val ciScore = col("成绩")          // "成绩"在"成绩标识"之前，indexOfFirst 命中"成绩"
        val ciCategory = col("课程性质", "修读性质")
        val ciAttempt = col("考试性质", "修读方式", "成绩性质")

        return rows.drop(1).mapNotNull { row ->
            val cells = CELL.findAll(row).map { clean(it.groupValues[1]) }.toList()
            fun at(i: Int): String = if (i in cells.indices) cells[i] else ""
            val name = at(ciName).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val term = at(ciTerm).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val score = at(ciScore)
            val point = BitGpaConverter.toGradePoint(score)
            GradeEntryEntity(
                termCode = term,
                termName = term,
                courseName = name,
                courseCode = at(ciCode),
                credit = at(ciCredit).toDoubleOrNull() ?: 0.0,
                score = score,
                gradePoint = point,
                gradeLetter = null,
                category = at(ciCategory).ifBlank { null },
                // 正方"考试性质"形如"正常考试"/"重修"/"补考"——归一化:含"正常"即视为正常
                // (否则每门正常课都会被高亮)，重修/补考才保留原值。
                attemptType = at(ciAttempt).let { if (it.isBlank() || "正常" in it) "正常" else it },
                isPass = computePass(point, score),
                fetchedAt = fetchedAt,
            )
        }
    }

    private fun computePass(point: Double?, score: String): Boolean {
        if (point != null) return point > 0.0
        if (listOf("不及格", "不合格", "不通过", "缺考", "缓考", "作弊").any { it in score }) return false
        if (listOf("优", "良", "中", "及格", "合格", "通过").any { it in score }) return true
        score.toDoubleOrNull()?.let { return it >= 60.0 }
        return true
    }

    /** Strip inner tags, collapse whitespace, decode the few entities BIT uses. */
    private fun clean(cell: String): String = cell
        .replace(TAG, "")
        .replace("&nbsp;", " ").replace("&amp;", "&")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace(WS, " ").trim()

    companion object {
        private val TABLE = Regex(
            """<table[^>]*\bid=["']dataList["'][^>]*>(.*?)</table>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        private val ROW = Regex("""<tr[^>]*>(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
        private val CELL = Regex("""<t[dh][^>]*>(.*?)</t[dh]>""", RegexOption.DOT_MATCHES_ALL)
        private val TAG = Regex("""<[^>]*>""")
        private val WS = Regex("""\s+""")
    }
}
