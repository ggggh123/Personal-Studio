# 成绩详情增强 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 成绩查询页课程展开后，从只显示「平均分/班级%/专业%」扩展为分组三行（课程属性 / 成绩对比 / 排名），并补抓 cjfx 详情里的班级人数、专业人数、全校百分位。

**Architecture:** 沿用现有「主列表解析 + cjfx 详情 enrich → 落 Room → 映射 GradeItem → Compose 展示」管线。解析器多取 3 个字段；实体/模型/映射各加 3 列；前台 `SyncGradesUseCase.enrich` 与后台 `GradePollWorker.enrichOne` 两处都拷新字段；UI 抽出纯函数 `gradeDetailLines` 渲染分组三行。

**Tech Stack:** Kotlin, Jetpack Compose, Room, JUnit4 + mockk, Gradle (Windows `.\gradlew.bat`)。dev 库 `fallbackToDestructiveMigration` 已启用，加列不写迁移。

---

### Task 1: 解析器多取 3 个字段（班级人数 / 专业人数 / 全校百分位）

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/domain/bitgrades/JsxsdDetailParser.kt`
- Test: `app/src/test/java/com/example/personal_studio/domain/bitgrades/JsxsdDetailParserTest.kt`

- [ ] **Step 1: 给现有测试加 3 个字段断言（先失败）**

在 `JsxsdDetailParserTest.kt` 的 `parses avg and rank percentiles from real cjfx cells` 测试末尾（`assertEquals("63%", d.majorRankText)` 之后）追加：

```kotlin
        assertEquals(18, d.classSize)
        assertEquals(32, d.majorSize)
        assertEquals("46%", d.schoolRankText)
```

- [ ] **Step 2: 跑测试确认编译失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.domain.bitgrades.JsxsdDetailParserTest"`
Expected: 编译失败（`DetailInfo` 无 `classSize/majorSize/schoolRankText` 属性，`Unresolved reference`）。

- [ ] **Step 3: 在 DetailInfo 加 3 字段**

把 `JsxsdDetailParser.kt` 的 `DetailInfo` 改为：

```kotlin
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
```

- [ ] **Step 4: 在 parse() 里解析这 3 个字段**

把 `parse()` 末尾的 `return DetailInfo(...)` 整块替换为（其余不变）：

```kotlin
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
```

> 说明：`find` 是子串匹配 map 的键。键来自 `标签：值` 的标签部分（如「班级人数」「本人成绩在所有学生中占」）。「班级人数」不会撞「班级中占」，「专业人数」不撞「专业中占」，「所有学生中占」专指全校——三者互不冲突。

- [ ] **Step 5: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.domain.bitgrades.JsxsdDetailParserTest"`
Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/domain/bitgrades/JsxsdDetailParser.kt app/src/test/java/com/example/personal_studio/domain/bitgrades/JsxsdDetailParserTest.kt
git commit -m "feat(grades): cjfx 详情多解析班级人数/专业人数/全校百分位"
```

---

### Task 2: 实体 + 领域模型 + 映射各加 3 列

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/data/local/db/entity/GradeEntryEntity.kt`
- Modify: `app/src/main/java/com/example/personal_studio/domain/bitgrades/model/GradeModels.kt`
- Modify: `app/src/main/java/com/example/personal_studio/domain/bitgrades/ComputeGpaUseCase.kt`

- [ ] **Step 1: GradeEntryEntity 加 3 列**

在 `GradeEntryEntity.kt` 的 `courseStudyCount` 那行之后（data class 末尾）追加：

```kotlin
    val classSize: Int? = null,           // cjfx 班级人数
    val majorSize: Int? = null,           // cjfx 专业人数
    val schoolRankText: String? = null,   // 本人成绩在所有学生中占(全校百分位)
```

- [ ] **Step 2: GradeItem 加 3 字段**

在 `GradeModels.kt` 的 `GradeItem` 里，`courseStudyCount` 那行之后追加：

```kotlin
    val classSize: Int? = null,           // cjfx 班级人数
    val majorSize: Int? = null,           // cjfx 专业人数
    val schoolRankText: String? = null,   // 全校百分位
```

- [ ] **Step 3: ComputeGpaUseCase.toItem() 映射 3 字段**

把 `ComputeGpaUseCase.kt` 的 `toItem()` 替换为：

```kotlin
    private fun GradeEntryEntity.toItem() = GradeItem(
        courseName, courseCode, credit, score, gradePoint, gradeLetter, category, attemptType, isPass,
        courseAvg = courseAvg, classRankText = classRankText, majorRankText = majorRankText,
        id = id,
        courseMaxScore = courseMaxScore, courseStudyCount = courseStudyCount,
        classSize = classSize, majorSize = majorSize, schoolRankText = schoolRankText,
    )
```

- [ ] **Step 4: 编译确认（结构改动，编译即验证）**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/data/local/db/entity/GradeEntryEntity.kt app/src/main/java/com/example/personal_studio/domain/bitgrades/model/GradeModels.kt app/src/main/java/com/example/personal_studio/domain/bitgrades/ComputeGpaUseCase.kt
git commit -m "feat(grades): 实体/模型/映射加 班级人数/专业人数/全校百分位 三列"
```

---

### Task 3: 前台 + 后台 enrich 拷贝新字段

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/domain/bitgrades/SyncGradesUseCase.kt:108-114`
- Modify: `app/src/main/java/com/example/personal_studio/core/workers/GradePollWorker.kt`（`enrichOne` 的 `e.copy(...)`）

- [ ] **Step 1: 前台 enrich 加 3 字段**

把 `SyncGradesUseCase.kt` 的 `enrich()` 里 `return e.copy(...)` 替换为：

```kotlin
        return e.copy(
            courseAvg = info.courseAvg,
            courseMaxScore = info.courseMaxScore,
            courseStudyCount = info.courseStudyCount,
            classRankText = info.classRankText,
            majorRankText = info.majorRankText,
            classSize = info.classSize,
            majorSize = info.majorSize,
            schoolRankText = info.schoolRankText,
        )
```

- [ ] **Step 2: 后台 enrichOne 加 3 字段**

把 `GradePollWorker.kt` 的 `enrichOne()` 里 `return e.copy(...)` 替换为：

```kotlin
        return e.copy(
            courseAvg = info.courseAvg,
            courseMaxScore = info.courseMaxScore,
            courseStudyCount = info.courseStudyCount,
            classRankText = info.classRankText,
            majorRankText = info.majorRankText,
            classSize = info.classSize,
            majorSize = info.majorSize,
            schoolRankText = info.schoolRankText,
        )
```

- [ ] **Step 3: 编译确认**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/domain/bitgrades/SyncGradesUseCase.kt app/src/main/java/com/example/personal_studio/core/workers/GradePollWorker.kt
git commit -m "feat(grades): 前台/后台 enrich 拷贝新增 3 个详情字段"
```

---

### Task 4: UI 分组三行（纯函数 + 渲染）

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/GradeDetailLines.kt`
- Test: `app/src/test/java/com/example/personal_studio/feature/bitgrades/ui/components/GradeDetailLinesTest.kt`
- Modify: `app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/TermGradeSection.kt:104-116`

- [ ] **Step 1: 写纯函数的失败测试**

创建 `app/src/test/java/com/example/personal_studio/feature/bitgrades/ui/components/GradeDetailLinesTest.kt`：

```kotlin
package com.example.personal_studio.feature.bitgrades.ui.components

import com.example.personal_studio.domain.bitgrades.model.GradeItem
import org.junit.Assert.assertEquals
import org.junit.Test

class GradeDetailLinesTest {

    @Test fun `full item produces three grouped lines`() {
        val c = GradeItem(
            "高等数学", "C1", 4.0, "92", 4.0, null, "必修", "正常", true,
            courseAvg = 78.7, classRankText = "67%", majorRankText = "63%",
            courseMaxScore = 100.0, courseStudyCount = 1178,
            classSize = 18, majorSize = 32, schoolRankText = "46%",
        )
        val lines = gradeDetailLines(c)
        assertEquals(3, lines.size)
        assertEquals("必修  ·  绩点 4.0", lines[0])
        assertEquals("平均 78.7  ·  最高 100  ·  修学 1178人", lines[1])
        assertEquals("班级 67%(18人)  ·  专业 63%(32人)  ·  全校 46%", lines[2])
    }

    @Test fun `empty item yields no lines`() {
        val c = GradeItem("体育", "C2", 1.0, "通过", null, null, null, "正常", true)
        assertEquals(emptyList<String>(), gradeDetailLines(c))
    }

    @Test fun `only category and gradePoint yields one line`() {
        val c = GradeItem("思修", "C3", 2.0, "85", 3.7, null, "必修", "正常", true)
        val lines = gradeDetailLines(c)
        assertEquals(1, lines.size)
        assertEquals("必修  ·  绩点 3.7", lines[0])
    }

    @Test fun `rank without sizes omits parentheses`() {
        val c = GradeItem(
            "x", "C4", 3.0, "80", 3.0, null, null, "正常", true,
            classRankText = "50%",
        )
        assertEquals(listOf("班级 50%"), gradeDetailLines(c))
    }
}
```

- [ ] **Step 2: 跑测试确认编译失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.feature.bitgrades.ui.components.GradeDetailLinesTest"`
Expected: 编译失败（`Unresolved reference: gradeDetailLines`）。

- [ ] **Step 3: 实现纯函数**

创建 `app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/GradeDetailLines.kt`：

```kotlin
package com.example.personal_studio.feature.bitgrades.ui.components

import com.example.personal_studio.domain.bitgrades.model.GradeItem
import java.util.Locale

/**
 * 课程展开后的详情，分 3 组：课程属性 / 成绩对比 / 排名。
 * 每行内 null 项省略；整行全 null 则不产出该行；全部为空返回空列表（UI 显示「无详情」）。
 * 抽成纯函数便于单测（不依赖 Compose）。
 */
fun gradeDetailLines(c: GradeItem): List<String> {
    val attrs = buildList {
        c.category?.takeIf { it.isNotBlank() }?.let { add(it) }
        c.gradePoint?.let { add("绩点 " + String.format(Locale.US, "%.1f", it)) }
    }
    val scores = buildList {
        c.courseAvg?.let { add("平均 " + String.format(Locale.US, "%.1f", it)) }
        c.courseMaxScore?.let { add("最高 " + fmtScore(it)) }
        c.courseStudyCount?.let { add("修学 ${it}人") }
    }
    val ranks = buildList {
        c.classRankText?.let { add("班级 $it" + (c.classSize?.let { n -> "(${n}人)" } ?: "")) }
        c.majorRankText?.let { add("专业 $it" + (c.majorSize?.let { n -> "(${n}人)" } ?: "")) }
        c.schoolRankText?.let { add("全校 $it") }
    }
    return listOf(attrs, scores, ranks)
        .filter { it.isNotEmpty() }
        .map { it.joinToString("  ·  ") }
}

/** 整数分去掉小数（最高 100 而非 100.0），否则保 1 位。 */
private fun fmtScore(d: Double): String =
    if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.US, "%.1f", d)
```

- [ ] **Step 4: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.feature.bitgrades.ui.components.GradeDetailLinesTest"`
Expected: PASS（4 个用例）。

- [ ] **Step 5: TermGradeSection 渲染分组三行**

把 `TermGradeSection.kt` 里 `AnimatedVisibility(courseExpanded) { ... }` 整块（当前的 `val parts = buildList {...}` + 单个 `Text(...)`）替换为：

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

> `gradeDetailLines` 同包，无需 import。`Column`/`Modifier`/`padding`/`Text`/`MaterialTheme`/`Cyan`/`FoamDim`/`dp` 均已在该文件 import。`Locale` 此前仅 `parts` 用到——若替换后 `TermGradeSection.kt` 不再引用 `Locale`（文件顶部 GPA 格式化 `String.format(Locale.US, ...)` 仍在用），保留其 import；不要误删。

- [ ] **Step 6: 编译 + 全量单测**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL，全绿。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/GradeDetailLines.kt app/src/test/java/com/example/personal_studio/feature/bitgrades/ui/components/GradeDetailLinesTest.kt app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/TermGradeSection.kt
git commit -m "feat(grades): 课程详情改分组三行(课程属性/成绩对比/排名)"
```

---

### Task 5: 装真机验证

**Files:** 无（验证环节）

- [ ] **Step 1: 装真机**

Run: `.\gradlew.bat :app:installDebug`
Expected: `Installed on 1 device.`

- [ ] **Step 2: 真机 DoD（手动）**

1. 进成绩页，**重新同步一次**（让新 3 字段入库——旧数据没有）。
2. 展开任一学期 → 展开任一课程，确认看到三行：课程属性（必修/选修 · 绩点）、成绩对比（平均 · 最高 · 修学人数）、排名（班级%(人数) · 专业%(人数) · 全校%）。
3. 找一门只有部分字段的课，确认空项省略、不出空行；完全无详情的课显示「无详情」。

- [ ] **Step 3: 推分支（开 PR）**

```bash
git push -u origin feat/grade-detail-richer
```

---

## Self-Review

**1. Spec coverage:**
- 多解析 班级人数/专业人数/全校百分位 → Task 1 ✓
- 实体/模型/映射加 3 列 → Task 2 ✓
- 前台 + 后台 enrich 两处都补 → Task 3 ✓
- UI 分组三行 + null 省略 + 「无详情」 → Task 4 ✓
- 解析器测试加断言 → Task 1 Step 1 ✓；UI 纯函数测试 → Task 4 ✓
- 「需重新同步一次才有值」提醒 → Task 5 Step 2 ✓

**2. Placeholder scan:** 无 TBD/TODO；每个改动均给出完整代码。

**3. Type consistency:** `DetailInfo` 新增 `classSize/majorSize/schoolRankText`（Task 1）与 entity（Task 2）、`GradeItem`（Task 2）、enrich 拷贝（Task 3）、`gradeDetailLines` 读取（Task 4）字段名一致。`gradeDetailLines` 在 Task 4 定义、Task 4 Step 5 调用，签名一致 `(GradeItem): List<String>`。
