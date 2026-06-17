# 成绩课程详情可视化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 成绩页课程展开详情从纯文本升级为终端方块条可视化(排名百分位条 + 你/平均/最高 成绩对比条)。

**Architecture:** 纯计算抽到可单测的 `GradeVizMath`(parseTopPercent/scoreToFloat/fmtScore/scoreAxisLo)。新建 `CourseDetailViz` 组合件(含私有 `PercentileBar`/`ScoreRangeBar`,Compose Box 实心填充 + 档位配色)取代 `TermGradeSection` 里 `gradeDetailLines` 文本渲染;退役死代码 `gradeDetailLines` + 其测试。

**Tech Stack:** Kotlin, Jetpack Compose(Box/BoxWithConstraints 填充条), JUnit4。

**约定：** 提交结尾 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。中文回复。`./gradlew` / 真机 `./gradlew :app:installDebug`。

---

### Task 1: GradeVizMath 纯函数 + 单测(TDD)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/GradeVizMath.kt`
- Test: `app/src/test/java/com/example/personal_studio/feature/bitgrades/ui/components/GradeVizMathTest.kt`

- [ ] **Step 1: 写失败测试**

创建 `GradeVizMathTest.kt`：

```kotlin
package com.example.personal_studio.feature.bitgrades.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradeVizMathTest {

    @Test fun `parseTopPercent extracts top percent`() {
        assertEquals(20, parseTopPercent("前20%"))
        assertEquals(5, parseTopPercent("前5%"))
        assertEquals(20, parseTopPercent("20%"))
        assertEquals(100, parseTopPercent("前100%"))
    }

    @Test fun `parseTopPercent returns null for non-percent`() {
        assertNull(parseTopPercent(""))
        assertNull(parseTopPercent("优"))
        assertNull(parseTopPercent(null))
        assertNull(parseTopPercent("前0%"))
    }

    @Test fun `scoreToFloat parses numeric scores only`() {
        assertEquals(92f, scoreToFloat("92"))
        assertEquals(92.5f, scoreToFloat(" 92.5 "))
        assertNull(scoreToFloat("A"))
        assertNull(scoreToFloat("优"))
    }

    @Test fun `fmtScore drops trailing zero`() {
        assertEquals("98", fmtScore(98.0))
        assertEquals("92.5", fmtScore(92.5))
    }

    @Test fun `scoreAxisLo floors to ten and clamps at zero`() {
        assertEquals(70f, scoreAxisLo(78.0, 92f), 0.001f)
        assertEquals(40f, scoreAxisLo(55.0, 45f), 0.001f)
        assertEquals(0f, scoreAxisLo(3.0, 5f), 0.001f)
    }
}
```

- [ ] **Step 2: 运行确认失败** — Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.feature.bitgrades.ui.components.GradeVizMathTest"` Expected: FAIL（函数未定义）。

- [ ] **Step 3: 写实现**

创建 `GradeVizMath.kt`：

```kotlin
package com.example.personal_studio.feature.bitgrades.ui.components

import java.util.Locale
import kotlin.math.floor

/** 从 "前20%"/"前5%"/"20%" 抽出顶部百分数(整数 1..100);无有效数字返回 null。 */
fun parseTopPercent(text: String?): Int? {
    if (text.isNullOrBlank()) return null
    val raw = Regex("""(\d+(?:\.\d+)?)\s*%""").find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
    return raw.toInt().takeIf { it in 1..100 }
}

/** 分数字符串转 Float;等级制/「优」等非数字返回 null。 */
fun scoreToFloat(score: String): Float? = score.trim().toFloatOrNull()

/** 整数分去小数(98.0→"98"),否则保 1 位。 */
fun fmtScore(d: Double): String =
    if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.US, "%.1f", d)

/** 成绩对比条轴下界:min(平均,你) 向下取整到 10,且 ≥ 0。 */
fun scoreAxisLo(avg: Double, you: Float): Float =
    (floor(minOf(avg.toFloat(), you) / 10f) * 10f).coerceAtLeast(0f)
```

- [ ] **Step 4: 运行确认通过** — Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.feature.bitgrades.ui.components.GradeVizMathTest"` Expected: PASS（5 个）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/GradeVizMath.kt \
        app/src/test/java/com/example/personal_studio/feature/bitgrades/ui/components/GradeVizMathTest.kt
git commit -m "feat(grades): 成绩详情可视化纯函数 GradeVizMath(parseTopPercent/scoreToFloat/fmtScore/scoreAxisLo)+ 单测

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: CourseDetailViz 组合件 + 接入 + 退役 gradeDetailLines

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/CourseDetailViz.kt`
- Modify: `app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/TermGradeSection.kt`（展开块换 `CourseDetailViz(c)` + 删无用 import）
- Delete: `app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/GradeDetailLines.kt`、`app/src/test/java/com/example/personal_studio/feature/bitgrades/ui/components/GradeDetailLinesTest.kt`

- [ ] **Step 1: 写 CourseDetailViz**

创建 `CourseDetailViz.kt`：

```kotlin
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
        Text(
            rankText + (size?.let { "·${it}人" } ?: ""),
            color = tier, style = MaterialTheme.typography.labelMedium, maxLines = 1,
        )
    }
}

/** 成绩对比条:轴 [lo, max],填充到你(Phosphor),平均处打 Amber 竖刻度;下方三值标签。 */
@Composable
private fun ScoreRangeBar(you: Float, avg: Double, max: Double) {
    val lo = scoreAxisLo(avg, you)
    val hi = maxOf(max.toFloat(), you)
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
```

- [ ] **Step 2: TermGradeSection 接入 CourseDetailViz**

在 `TermGradeSection.kt` 把展开块（约第 104-121 行）：
```kotlin
                        AnimatedVisibility(courseExpanded) {
                            val lines = gradeDetailLines(c)
                            Column(Modifier.padding(start = 28.dp, top = 1.dp, bottom = 3.dp)) {
                                if (lines.isEmpty()) {
                                    Text(
                                        "无详情", color = FoamDim,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                } else {
                                    lines.forEach { line ->
                                        Text(
                                            line, color = Cyan,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        }
```
替换为：
```kotlin
                        AnimatedVisibility(courseExpanded) {
                            CourseDetailViz(c)
                        }
```

- [ ] **Step 3: TermGradeSection 删去因此变得无用的 import**

`Cyan` 与 `MaterialTheme` 在 `TermGradeSection` 中仅被上面删掉的详情块用到。把这两行 import 删除：
```kotlin
import androidx.compose.material3.MaterialTheme
```
```kotlin
import com.example.personal_studio.ui.theme.Cyan
```
（其余 import 仍在用,保留。`FoamDim` 仍被课程名/排除态用到,保留。）

- [ ] **Step 4: 删除退役的 gradeDetailLines + 其测试**

```bash
git rm app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/GradeDetailLines.kt \
       app/src/test/java/com/example/personal_studio/feature/bitgrades/ui/components/GradeDetailLinesTest.kt
```

- [ ] **Step 5: 编译验证** — Run: `./gradlew :app:compileDebugKotlin` Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: 全量单测回归** — Run: `./gradlew :app:testDebugUnitTest` Expected: BUILD SUCCESSFUL（含 `GradeVizMathTest`;已删 `GradeDetailLinesTest` 不再编译）。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/CourseDetailViz.kt \
        app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/TermGradeSection.kt
git commit -m "feat(grades): 课程展开详情改可视化(排名百分位条+成绩对比条),退役 gradeDetailLines

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 完成后

全部 Task 完成且 `./gradlew :app:testDebugUnitTest` 全绿后,用 **superpowers:finishing-a-development-branch** 收尾(真机 DoD → 合并 main → 推 GitHub → 更新记忆)。

**真机 DoD：** 成绩页 → 展开学期 → 展开课程:① 班级/专业/全校三条百分位条按 前X% 填充、档位配色对(靠前绿/中黄/靠后红)、右侧附 `前X%·N人`;② 成绩对比条 你(绿填充)/平均(黄刻度)/最高(右端)位置合理,下方三值标签;③ 等级制课(score 非数字)成绩对比退回文本不崩;④ 无 cjfx 详情的课显「无详情」。

## Self-Review 记录

- **Spec 覆盖**：① 组件结构(GradeVizMath/CourseDetailViz)→Task 1+2;② 排名条→Task 2 PercentileBar;③ 成绩对比条→Task 2 ScoreRangeBar;④ 属性行&兜底→Task 2 CourseDetailViz;退役 gradeDetailLines→Task 2 Step4。全覆盖。
- **占位符**：无;每步含完整代码 + 确切命令。
- **类型一致**：`parseTopPercent/scoreToFloat/fmtScore/scoreAxisLo` 在 Task 1 定义、Task 2 调用一致;`CourseDetailViz(c: GradeItem)` 在 Task 2 定义、TermGradeSection 调用一致;`GradeItem` 字段(score/courseAvg/courseMaxScore/classRankText/majorRankText/schoolRankText/classSize/majorSize/courseStudyCount/gradePoint/category)与模型一致。
