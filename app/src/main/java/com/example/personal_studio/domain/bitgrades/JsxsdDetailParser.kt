package com.example.personal_studio.domain.bitgrades

import javax.inject.Inject

/** 解析正方 cjfx(成绩分析) 详情页。页面是一堆 `<td>标签：值</td>`，按"："切分取值。
 *  字段标签按子串匹配(真机 F1-B 验证确认)。 */
class JsxsdDetailParser @Inject constructor() {

    data class DetailInfo(
        val courseAvg: Double?,
        val courseMaxScore: Double?,
        val courseStudyCount: Int?,
        val classRankText: String?,
        val majorRankText: String?,
        val classSize: Int?,
        val majorSize: Int?,
        val schoolRankText: String?,
    )

    fun parse(html: String): DetailInfo {
        val map = HashMap<String, String>()
        CELL.findAll(html).forEach { m ->
            val text = m.groupValues[1].replace(TAG, "").replace("&nbsp;", " ")
                .replace(WS, " ").trim()
            val idx = text.indexOf('：')
            if (idx > 0 && idx < text.length - 1) {
                map[text.substring(0, idx).trim()] = text.substring(idx + 1).trim()
            }
        }
        fun find(vararg keys: String): String? =
            map.entries.firstOrNull { e -> keys.any { it in e.key } }?.value?.takeIf { it.isNotBlank() }
        val maxScore = find("最高分")?.toDoubleOrNull()
        val studyCount = find("学习人数")?.let { v -> Regex("""\d+""").find(v)?.value?.toIntOrNull() }
        val classSize = find("班级人数")?.let { v -> Regex("""\d+""").find(v)?.value?.toIntOrNull() }
        val majorSize = find("专业人数")?.let { v -> Regex("""\d+""").find(v)?.value?.toIntOrNull() }
        return DetailInfo(
            courseAvg = find("平均分")?.toDoubleOrNull(),
            courseMaxScore = maxScore,
            courseStudyCount = studyCount,
            classRankText = find("班级中占", "班级排名"),
            majorRankText = find("专业中占", "专业排名", "年级"),
            classSize = classSize,
            majorSize = majorSize,
            schoolRankText = find("所有学生中占", "全校中占", "全校"),
        )
    }

    companion object {
        private val CELL = Regex("""<td[^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL)
        private val TAG = Regex("""<[^>]*>""")
        private val WS = Regex("""\s+""")
    }
}
