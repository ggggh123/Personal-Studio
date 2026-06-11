# 专业排名百分位估计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用各门课的「专业中占 p%」逐课位次，z 空间学分加权聚合出学生在本专业的整体排名百分位（点估计 + 区间），显示在成绩页概览卡。

**Architecture:** 新增纯函数 `RankPercentileEstimator`（输出 `RankPercentileEstimate`），复用并扩充 `PeerGpaEstimator`（补正态 CDF）。`ComputeGpaUseCase` 解析各课 `majorRankText` → 调估计器 → 挂到 `GradeBook.overallMajorRankEst`。`GpaOverviewCard` 加一行展示。只用老字段 `majorRankText`，不需重新同步，无 DB/网络改动。

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4, Gradle（Windows `.\gradlew.bat`）。

---

### Task 1: `PeerGpaEstimator` 补正态 CDF `normalCdf`

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/core/util/PeerGpaEstimator.kt`
- Test: `app/src/test/java/com/example/personal_studio/core/util/PeerGpaEstimatorTest.kt`

- [ ] **Step 1: 写失败测试**

在 `PeerGpaEstimatorTest.kt` 类体内追加（若文件不存在则创建，包名 `com.example.personal_studio.core.util`，加 `import org.junit.Assert.assertEquals` / `import org.junit.Test`）：

```kotlin
    @Test fun `normalCdf matches known values`() {
        assertEquals(0.5, PeerGpaEstimator.normalCdf(0.0), 1e-6)
        assertEquals(0.975, PeerGpaEstimator.normalCdf(1.96), 2e-3)
        assertEquals(0.8413, PeerGpaEstimator.normalCdf(1.0), 2e-3)
    }

    @Test fun `normalCdf is symmetric`() {
        assertEquals(1.0 - PeerGpaEstimator.normalCdf(1.3), PeerGpaEstimator.normalCdf(-1.3), 1e-9)
    }

    @Test fun `normalCdf inverts invNormalCdf`() {
        assertEquals(0.8, PeerGpaEstimator.normalCdf(PeerGpaEstimator.invNormalCdf(0.8)), 2e-3)
    }
```

- [ ] **Step 2: 跑测试确认编译失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.core.util.PeerGpaEstimatorTest"`
Expected: 编译失败（`Unresolved reference: normalCdf`）。

- [ ] **Step 3: 实现 normalCdf**

在 `PeerGpaEstimator.kt`：顶部 import 区把 `import kotlin.math.ln` / `import kotlin.math.sqrt` 旁补两行：

```kotlin
import kotlin.math.abs
import kotlin.math.exp
```

在 `invNormalCdf(...)` 函数之后、`expectedMaxZ` 之前插入：

```kotlin
    /** 标准正态 CDF Φ(z)(Abramowitz-Stegun 26.2.17 有理逼近,精度 ~7.5e-8)。 */
    fun normalCdf(z: Double): Double {
        val t = 1.0 / (1.0 + 0.2316419 * abs(z))
        val phi = 0.3989422804014327 * exp(-z * z / 2.0)   // φ(z) = 1/√(2π)·e^(−z²/2)
        val poly = t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 +
            t * (-1.821255978 + t * 1.330274429))))
        val tail = phi * poly
        return if (z >= 0.0) 1.0 - tail else tail
    }
```

- [ ] **Step 4: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.core.util.PeerGpaEstimatorTest"`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/core/util/PeerGpaEstimator.kt app/src/test/java/com/example/personal_studio/core/util/PeerGpaEstimatorTest.kt
git commit -m "feat(grades): PeerGpaEstimator 补正态 CDF normalCdf"
```

---

### Task 2: `RankPercentileEstimator` 核心估计器

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/core/util/RankPercentileEstimator.kt`
- Test: `app/src/test/java/com/example/personal_studio/core/util/RankPercentileEstimatorTest.kt`

- [ ] **Step 1: 写失败测试**

创建 `app/src/test/java/com/example/personal_studio/core/util/RankPercentileEstimatorTest.kt`：

```kotlin
package com.example.personal_studio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankPercentileEstimatorTest {

    // (credit, majorPercentile p, majorSize)
    private fun c(p: Double, credit: Double = 3.0, n: Int? = 30) = Triple(credit, p, n)

    @Test fun `all median maps to about 50 percent`() {
        val r = RankPercentileEstimator.estimate(listOf(c(50.0), c(50.0), c(50.0)))!!
        assertEquals(50.0, r.pointPercent, 1.0)
        assertEquals(50.0, r.loPercent, 1.0)
        assertEquals(50.0, r.hiPercent, 1.0)
    }

    @Test fun `strong consistent yields small point within interval`() {
        val r = RankPercentileEstimator.estimate(listOf(c(10.0), c(12.0), c(15.0)))!!
        assertTrue("point ${r.pointPercent} should be <15", r.pointPercent < 15.0)
        assertTrue("point ${r.pointPercent} should be >5", r.pointPercent > 5.0)
        assertTrue(r.loPercent <= r.pointPercent)
        assertTrue(r.pointPercent <= r.hiPercent)
        assertEquals(3, r.basisCount)
    }

    @Test fun `smaller percentiles give a smaller point estimate`() {
        val strong = RankPercentileEstimator.estimate(listOf(c(5.0), c(8.0), c(6.0)))!!
        val weak = RankPercentileEstimator.estimate(listOf(c(40.0), c(45.0), c(42.0)))!!
        assertTrue(strong.pointPercent < weak.pointPercent)
    }

    @Test fun `fewer than two courses yields null`() {
        assertNull(RankPercentileEstimator.estimate(emptyList()))
        assertNull(RankPercentileEstimator.estimate(listOf(c(20.0))))
    }

    @Test fun `interval invariants and bounds hold`() {
        val r = RankPercentileEstimator.estimate(listOf(c(2.0), c(30.0), c(70.0), c(95.0)))!!
        assertTrue(r.loPercent in 1.0..99.0)
        assertTrue(r.hiPercent in 1.0..99.0)
        assertTrue(r.pointPercent in 1.0..99.0)
        assertTrue(r.loPercent <= r.pointPercent && r.pointPercent <= r.hiPercent)
    }

    @Test fun `perfect score does not blow up`() {
        val r = RankPercentileEstimator.estimate(listOf(c(0.0), c(20.0)))!!
        assertTrue(r.pointPercent.isFinite())
        assertTrue(r.pointPercent in 1.0..99.0)
    }

    @Test fun `higher credit on the stronger course pulls point lower`() {
        val heavyStrong = RankPercentileEstimator.estimate(
            listOf(c(p = 5.0, credit = 6.0), c(p = 60.0, credit = 1.0)),
        )!!
        val heavyWeak = RankPercentileEstimator.estimate(
            listOf(c(p = 5.0, credit = 1.0), c(p = 60.0, credit = 6.0)),
        )!!
        assertTrue(heavyStrong.pointPercent < heavyWeak.pointPercent)
    }
}
```

- [ ] **Step 2: 跑测试确认编译失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.core.util.RankPercentileEstimatorTest"`
Expected: 编译失败（`Unresolved reference: RankPercentileEstimator`）。

- [ ] **Step 3: 实现估计器**

创建 `app/src/main/java/com/example/personal_studio/core/util/RankPercentileEstimator.kt`：

```kotlin
package com.example.personal_studio.core.util

import kotlin.math.sqrt

/** 专业整体排名百分位估计结果。percent 均为「前 X%」语义,已夹 [1,99]。 */
data class RankPercentileEstimate(
    val pointPercent: Double,
    val loPercent: Double,
    val hiPercent: Double,
    val basisCount: Int,
)

/**
 * 用各课「专业中占 p%」(前 p%,小=好)逐课位次,z 空间学分加权聚合出整体专业百分位。
 * 点估计取 ρ=1 一致能力模型(保守);区间合成「各课分歧标准误」+「相关性敏感度(ρ_min)」。
 * 见 spec docs/superpowers/specs/2026-06-11-rank-percentile-estimator-design.md。
 */
object RankPercentileEstimator {
    private const val RHO_MIN = 0.5    // 相关性结构带下界
    private const val SE_K = 1.0       // 标准误倍数(≈68% 带)
    private const val EPS = 0.005      // 无专业人数时的连续性夹值
    private const val MIN_COURSES = 2  // 少于此不出估计

    /** courses: 各课 (credit, majorPercentile p∈[0,100], majorSize?)。不足/无效返回 null。 */
    fun estimate(courses: List<Triple<Double, Double, Int?>>): RankPercentileEstimate? {
        val valid = courses.filter { it.first > 0.0 && it.second in 0.0..100.0 }
        if (valid.size < MIN_COURSES) return null

        val w = valid.map { it.first }
        val z = valid.map { (_, p, n) ->
            val b = 1.0 - p / 100.0                       // 击败比例
            val lo = if (n != null && n >= 2) 1.0 / (2.0 * n) else EPS
            PeerGpaEstimator.invNormalCdf(b.coerceIn(lo, 1.0 - lo))
        }
        val sumW = w.sum()
        val sumW2 = w.sumOf { it * it }
        val sumWZ = w.indices.sumOf { w[it] * z[it] }
        val zStar = sumWZ / sumW                          // ρ=1 点估计

        val s2 = w.indices.sumOf { w[it] * (z[it] - zStar) * (z[it] - zStar) } / sumW
        val nEff = sumW * sumW / sumW2
        val se = sqrt(s2 / nEff)                          // 各课分歧标准误

        val denomRho = sqrt((1.0 - RHO_MIN) * sumW2 + RHO_MIN * sumW * sumW)
        val zRho = sumWZ / denomRho                       // 相关性敏感度(更极端一侧)

        val sign = if (zStar >= 0.0) 1.0 else -1.0
        val zLess = zStar - sign * SE_K * se              // 往 50% 收
        val zMore = zRho + sign * SE_K * se               // 往两端推

        fun pct(zz: Double) = (100.0 * (1.0 - PeerGpaEstimator.normalCdf(zz))).coerceIn(1.0, 99.0)
        val pPoint = pct(zStar)
        val pA = pct(zLess)
        val pB = pct(zMore)
        return RankPercentileEstimate(
            pointPercent = pPoint,
            loPercent = minOf(pA, pB),
            hiPercent = maxOf(pA, pB),
            basisCount = valid.size,
        )
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.core.util.RankPercentileEstimatorTest"`
Expected: PASS（7 个用例）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/core/util/RankPercentileEstimator.kt app/src/test/java/com/example/personal_studio/core/util/RankPercentileEstimatorTest.kt
git commit -m "feat(grades): RankPercentileEstimator 逐课位次 z 空间聚合估专业百分位"
```

---

### Task 3: `GradeBook` 加字段 + `ComputeGpaUseCase` 解析填充

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/domain/bitgrades/model/GradeModels.kt`
- Modify: `app/src/main/java/com/example/personal_studio/domain/bitgrades/ComputeGpaUseCase.kt`
- Test: `app/src/test/java/com/example/personal_studio/domain/bitgrades/ComputeGpaUseCaseTest.kt`

- [ ] **Step 1: 写失败测试**

在 `ComputeGpaUseCaseTest.kt` 类体内追加（顶部确保有 `import com.example.personal_studio.data.local.db.entity.GradeEntryEntity`、`import org.junit.Assert.assertNotNull`、`import org.junit.Assert.assertNull`、`import org.junit.Assert.assertTrue`、`import org.junit.Test`）：

```kotlin
    private fun rankEntry(major: String?, credit: Double = 3.0) = GradeEntryEntity(
        termCode = "2024", termName = "2024", courseName = "c", courseCode = "c$major$credit",
        credit = credit, score = "85", gradePoint = 3.0, gradeLetter = null, category = null,
        attemptType = "正常", isPass = true, fetchedAt = 0L, majorRankText = major, majorSize = 30,
    )

    @Test fun `book carries major rank estimate when percentiles present`() {
        val entries = listOf(rankEntry("10%"), rankEntry("12%"), rankEntry("15%"))
        val book = ComputeGpaUseCase().invoke(entries, emptyList())
        assertNotNull(book.overallMajorRankEst)
        assertTrue(book.overallMajorRankEst!!.pointPercent in 1.0..99.0)
    }

    @Test fun `no major rank estimate when percentiles absent`() {
        val entries = listOf(rankEntry(null), rankEntry(null))
        val book = ComputeGpaUseCase().invoke(entries, emptyList())
        assertNull(book.overallMajorRankEst)
    }
```

- [ ] **Step 2: 跑测试确认编译失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.domain.bitgrades.ComputeGpaUseCaseTest"`
Expected: 编译失败（`GradeBook` 无 `overallMajorRankEst`）。

- [ ] **Step 3: `GradeBook` 加字段**

在 `GradeModels.kt`：顶部加 `import com.example.personal_studio.core.util.RankPercentileEstimate`。在 `GradeBook` 的 `overallRank: TermRank?,` 之后加一字段：

```kotlin
    val overallMajorRankEst: RankPercentileEstimate? = null,
```

- [ ] **Step 4: `ComputeGpaUseCase` 解析 + 填充**

在 `ComputeGpaUseCase.kt`：顶部加
```kotlin
import com.example.personal_studio.core.util.RankPercentileEstimator
```
在 `invoke(...)` 里 `return GradeBook(` 之前插入：
```kotlin
        val rankCourses = entries.mapNotNull { e ->
            parseMajorPercent(e.majorRankText)?.let { p -> Triple(e.credit, p, e.majorSize) }
        }
        val majorRankEst = RankPercentileEstimator.estimate(rankCourses)
```
并在 `GradeBook(` 构造里 `overallRank = rankByTerm["OVERALL"]?.toTermRank(),` 之后加：
```kotlin
            overallMajorRankEst = majorRankEst,
```
在类体末尾（`toTermRank()` 之后、类结束 `}` 之前）加私有 helper：
```kotlin
    /** 「63%」/「前20%」→ 63.0/20.0;无数字或越界返回 null。 */
    private fun parseMajorPercent(text: String?): Double? {
        if (text == null) return null
        val v = Regex("""\d+(\.\d+)?""").find(text)?.value?.toDoubleOrNull() ?: return null
        return v.takeIf { it in 0.0..100.0 }
    }
```

- [ ] **Step 5: 跑测试确认通过 + 全量**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.personal_studio.domain.bitgrades.ComputeGpaUseCaseTest"`
Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/domain/bitgrades/model/GradeModels.kt app/src/main/java/com/example/personal_studio/domain/bitgrades/ComputeGpaUseCase.kt app/src/test/java/com/example/personal_studio/domain/bitgrades/ComputeGpaUseCaseTest.kt
git commit -m "feat(grades): GradeBook.overallMajorRankEst + ComputeGpaUseCase 解析专业位次"
```

---

### Task 4: 概览卡显示「专业排名」行

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/GpaOverviewCard.kt`
- Modify: `app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/GradesScreen.kt:87-94`

- [ ] **Step 1: `GpaOverviewCard` 加参数 + 渲染**

在 `GpaOverviewCard.kt`：顶部加两行 import：
```kotlin
import com.example.personal_studio.core.util.RankPercentileEstimate
import com.example.personal_studio.ui.theme.Amber
import kotlin.math.roundToInt
```
给 `@Composable fun GpaOverviewCard(` 的参数列表里、`filtering: Boolean = false,` 之前加一个参数：
```kotlin
    rankEst: RankPercentileEstimate? = null,
```
在 `if (peerGpa != null || peerAvgScore != null) { ... }` 这个块的**闭合 `}` 之后**、紧接着（仍在最外层 `Column` 内）插入：
```kotlin
        if (rankEst != null && !filtering) {
            Spacer(Modifier.height(4.dp))
            Text(
                "专业排名  约前 ${rankEst.pointPercent.roundToInt()}%" +
                    "（${rankEst.loPercent.roundToInt()}%–${rankEst.hiPercent.roundToInt()}%）",
                color = Amber,
                style = MaterialTheme.typography.labelMedium,
            )
        }
```

- [ ] **Step 2: `GradesScreen` 传入 rankEst**

在 `GradesScreen.kt` 的 `GpaOverviewCard(` 调用里（当前 `filtering = st.filtering,` 这一行之前）加：
```kotlin
                rankEst = st.book.overallMajorRankEst,
```

- [ ] **Step 3: 编译 + 全量单测**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL，全绿。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/GpaOverviewCard.kt app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/GradesScreen.kt
git commit -m "feat(grades): 概览卡显示估计专业排名(约前 X%(a%-b%))"
```

---

### Task 5: 装真机验证

**Files:** 无（验证环节）

- [ ] **Step 1: 装真机**

Run: `.\gradlew.bat :app:installDebug`
Expected: `Installed on 1 device.`

- [ ] **Step 2: 真机 DoD（手动）**

1. 进成绩页（已同步过、各课有专业位次的账号），概览卡「估计平均绩」下应出现 `专业排名  约前 X%（a%–b%）`，X 在 1–99 整数、a≤X≤b。
2. 勾选筛选子集时该行**消失**（只在总览显示）。
3. 若账号各课都没专业位次（极少），该行不出现（不报错）。

- [ ] **Step 3: 推分支（开 PR）**

```bash
git push -u origin feat/rank-percentile-estimator
```

---

## Self-Review

**1. Spec coverage:**
- z 换算 + 连续性校正 → Task 2 ✓
- ρ=1 点估计 → Task 2 (`zStar`) ✓
- 区间(分歧 SE + ρ 敏感度, sign 对称) → Task 2 ✓
- 夹 [1,99] + 整数显示 → Task 2 (`pct` coerce) + Task 4 (`roundToInt`) ✓
- normalCdf → Task 1 ✓
- 解析 majorRankText + ≥2 门 + GradeBook 字段 → Task 3 ✓
- 仅总览显示/Amber → Task 4 ✓
- 不需重新同步(用 majorRankText 老字段) → 无 schema/网络改动,各 Task 均不涉及 ✓
- 测试(估计器/normalCdf/ComputeGpaUseCase) → Task 1/2/3 ✓

**2. Placeholder scan:** 无 TBD/TODO;每步给出完整代码与命令。

**3. Type consistency:** `RankPercentileEstimate`(Task 2, core/util) 被 `GradeBook`(Task 3) 与 `GpaOverviewCard`(Task 4) import 一致;`RankPercentileEstimator.estimate(List<Triple<Double,Double,Int?>>)` 的入参在 Task 3 以 `Triple(e.credit, p, e.majorSize)` 构造一致;`pointPercent/loPercent/hiPercent/basisCount` 字段名在 Task 2 定义、Task 2/3/4 使用一致;`normalCdf`/`invNormalCdf` 签名在 Task 1 定义、Task 2 使用一致。
