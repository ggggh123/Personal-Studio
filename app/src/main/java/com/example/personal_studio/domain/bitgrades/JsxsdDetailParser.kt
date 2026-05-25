package com.example.personal_studio.domain.bitgrades

import javax.inject.Inject

/** 解析正方 cjfx(成绩分析) 详情页。页面是一堆 `<td>标签：值</td>`，按"："切分取值。
 *  字段标签按子串匹配(真机 F1-B 验证确认)。 */
class JsxsdDetailParser @Inject constructor() {

    data class DetailInfo(
        val courseAvg: Double?,
        val classRankText: String?,
        val majorRankText: String?,
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
        return DetailInfo(
            courseAvg = find("平均分")?.toDoubleOrNull(),
            classRankText = find("班级中占", "班级排名"),
            majorRankText = find("专业中占", "专业排名", "年级"),
        )
    }

    companion object {
        private val CELL = Regex("""<td[^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL)
        private val TAG = Regex("""<[^>]*>""")
        private val WS = Regex("""\s+""")
    }
}
