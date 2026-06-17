package com.example.personal_studio.feature.bitgrades.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.bitgrades.model.GradeItem
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import java.util.Locale

private val barShape = RoundedCornerShape(2.dp)

/** 课程展开详情可视化:属性行 + 成绩对比条 + 排名百分位条(各项缺失优雅退回文本/省略)。 */
@Composable
fun CourseDetailViz(c: GradeItem) {
    val you = scoreToFloat(c.score)
    val canScoreBar = you != null && c.courseAvg != null && c.courseMaxScore != null

    val attr = buildList {
        c.category?.takeIf { it.isNotBlank() }?.let { add(it) }
        c.gradePoint?.let { add("绩点 " + String.format(Locale.US, "%.1f", it)) }
    }
    val scoreText = buildList {
        c.courseAvg?.let { add("平均 ${fmtScore(it)}") }
        c.courseMaxScore?.let { add("最高 ${fmtScore(it)}") }
        c.courseStudyCount?.let { add("修学 ${it}人") }
    }
    val ranks = listOf(
        Triple("班级", c.classRankText, c.classSize),
        Triple("专业", c.majorRankText, c.majorSize),
        Triple("全校", c.schoolRankText, null),
    ).filter { it.second != null }

    val nothing = attr.isEmpty() && !canScoreBar && scoreText.isEmpty() && ranks.isEmpty()

    Column(Modifier.fillMaxWidth().padding(start = 28.dp, top = 2.dp, bottom = 4.dp)) {
        if (nothing) {
            Text("无详情", color = FoamDim, style = MaterialTheme.typography.labelMedium)
            return@Column
        }
        if (attr.isNotEmpty()) {
            Text(attr.joinToString("  ·  "), color = Cyan, style = MaterialTheme.typography.labelMedium)
        }
        if (canScoreBar) {
            ScoreRangeBar(you!!, c.courseAvg!!, c.courseMaxScore!!)
        } else if (scoreText.isNotEmpty()) {
            Text(scoreText.joinToString("  ·  "), color = Cyan, style = MaterialTheme.typography.labelMedium)
        }
        ranks.forEach { (label, text, size) ->
            val top = parseTopPercent(text)
            if (top != null) {
                PercentileBar(label, text!!, top, size)
            } else {
                Text(
                    "$label $text" + (size?.let { "(${it}人)" } ?: ""),
                    color = Cyan, style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** 排名百分位条:填充 = (100-top)%(你超过的同学比例);档位配色 靠前绿/中黄/靠后红。 */
@Composable
private fun PercentileBar(label: String, rankText: String, topPct: Int, size: Int?) {
    val ratio = (100 - topPct).coerceIn(0, 100) / 100f
    val tier = when {
        topPct <= 25 -> Phosphor
        topPct <= 60 -> Amber
        else -> Carmine
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = FoamMute, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(36.dp))
        Box(Modifier.weight(1f).height(10.dp).background(Rule, barShape)) {
            Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().background(tier, barShape))
        }
        Spacer(Modifier.width(8.dp))
        // 固定尾宽 → 三条排名条的 weight(1f) 等宽、右端对齐,便于一眼对比。
        Text(
            rankText + (size?.let { "·${it}人" } ?: ""),
            color = tier, style = MaterialTheme.typography.labelMedium, maxLines = 1,
            modifier = Modifier.width(96.dp),
        )
    }
}

/** 成绩对比条:轴 [lo, max],填充到你(Phosphor),平均处打 Amber 竖刻度;下方三值标签。 */
@Composable
private fun ScoreRangeBar(you: Float, avg: Double, max: Double) {
    val lo = 60f                 // 及格线作为轴下界
    val hi = max.toFloat()       // 课程最高分作为轴上界
    val span = (hi - lo).coerceAtLeast(1f)
    val youFrac = ((you - lo) / span).coerceIn(0f, 1f)
    val avgFrac = ((avg.toFloat() - lo) / span).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("成绩", color = FoamMute, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(36.dp))
            BoxWithConstraints(Modifier.weight(1f).height(10.dp)) {
                val barW = maxWidth
                Box(Modifier.fillMaxSize().background(Rule, barShape))
                Box(Modifier.fillMaxWidth(youFrac).fillMaxHeight().background(Phosphor, barShape))
                Box(Modifier.offset(x = barW * avgFrac).width(2.dp).fillMaxHeight().background(Amber))
            }
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = FoamDim)) { append("平均${fmtScore(avg)}  ") }
                withStyle(SpanStyle(color = Phosphor)) { append("你${fmtScore(you.toDouble())}  ") }
                withStyle(SpanStyle(color = FoamDim)) { append("最高${fmtScore(max)}") }
            },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 40.dp, top = 2.dp),
        )
    }
}
