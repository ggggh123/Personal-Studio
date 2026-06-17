# 成绩课程详情可视化 设计

日期：2026-06-17
状态：已获批，待写实现计划

## 背景与目标

成绩页（`GradesScreen`）概览层已有 Canvas 图表（GPA 趋势、成绩分布）。但点开某门课展开的**详情**（`TermGradeSection` 里）目前是 `gradeDetailLines` 产出的 **3 行纯文本**（Cyan）：① `类别 · 绩点` ② `平均 · 最高 · 修学人数` ③ `班级前X% · 专业前X% · 全校前X%`。目标：把这些数字升级为**终端方块条风可视化**，重点是班级/专业/全校排名百分位 + 你的分 vs 平均 vs 最高。

数据已全部入库（`GradeItem`：`score`、`courseAvg`、`courseMaxScore`、`courseStudyCount`、`classSize`、`majorSize`、`classRankText`/`majorRankText`/`schoolRankText`("前X%")、`gradePoint`、`category` 等）。无需改数据/DB/网络。

## 决策（已与用户确认）

- 范围：**排名条 + 成绩对比条 + 属性行**（不做估计分布曲线）。
- 风格：终端方块条（Compose Box 实心填充，非字符块）；档位配色 靠前绿/中黄/靠后红。

## ① 组件结构

- 新建 `feature/bitgrades/ui/components/CourseDetailViz.kt`：`@Composable CourseDetailViz(c: GradeItem)`，取代 `TermGradeSection` 展开块里原 `gradeDetailLines` 文本渲染。内含三段 + 两个私有条形件 `PercentileBar`、`ScoreRangeBar`。
- 新建 `feature/bitgrades/ui/components/GradeVizMath.kt`：纯函数（可单测，不依赖 Compose）
  - `parseTopPercent(text: String?): Int?` —— 从 `前20%`/`前5%`/`20%` 抽出顶部百分数 20/5/20；无数字 → null。
  - `scoreToFloat(score: String): Float?` —— `"92"`→92f、`"92.5"`→92.5f；等级制/「优」等非数字 → null。
  - `fmtScore(d: Double): String` —— 整数分去小数（`98.0`→`98`，否则保 1 位）。从原 `GradeDetailLines.fmtScore` 迁移。
- **移除** `GradeDetailLines.kt` 与 `GradeDetailLinesTest.kt`（换 viz 后死代码；仅 `TermGradeSection` 用过）。

## ② 排名条 `PercentileBar`（用户重点）

每条：左标签（班级/专业/全校）+ 终端方块条 + 右侧原文 `前20%`（+ `(N人)`）。
- `top = parseTopPercent(rankText)`；**填充比例 = `(100 - top)/100`**（即"你超过了百分之几的同学"）。
- **档位配色**：`top ≤ 25` → Phosphor；`25 < top ≤ 60` → Amber；`top > 60` → Carmine。
- 轨道底色 Rule；条用 `Box(fillMaxWidth*ratio).background(tierColor)`，高 ~8dp，0dp/微圆角，左右用 `▏ ▕` 或细边框界定。
- 某条 `rankText` 为 null → 不渲染该条；`rankText` 非空但 `parseTopPercent`=null → 退回该行**纯文本**（`班级 前20%(118人)` 样式）。

## ③ 成绩对比条 `ScoreRangeBar`

仅当 `you = scoreToFloat(c.score)` 非 null **且** `courseAvg`、`courseMaxScore` 均非 null 时渲染：
- 轴 `[lo, hi]`：`hi = max(courseMaxScore, you)`；`lo = floor(min(courseAvg, you) / 10) * 10`（取整到 10 留边，且 `lo` 不小于 0）。
- 填充到 `you`（Phosphor），在 `平均`/`你`/`最高` 三处按比例打竖刻度 + 小标签（`平均78`/`你92`/`最高98`）；`你` 高亮 Phosphor，余 FoamMute。
- 不满足条件（score 非数字 / 缺平均或最高）→ 退回纯文本行：`平均78 · 最高98 · 修学120人`（各项 null 省略，用 `fmtScore`）。

## ④ 属性行 & 兜底

- 属性行（文本）：`category` + `绩点 X.X`（各 null 省略，`category` 空串视为无）。
- 三段全部无可渲染内容（无属性、无成绩对比/文本、无任何排名）→ 显示「无详情」（同现状）。

## 测试

- 单测 `GradeVizMathTest`：`parseTopPercent`(`前20%`→20、`前5%`→5、`20%`→20、`""`/`优`/null→null)、`scoreToFloat`(`92`→92、`92.5`→92.5、`A`/`优`→null)、`fmtScore`(`98.0`→`98`、`92.5`→`92.5`)、轴范围辅助（若抽 `scoreAxis(lo,hi)`）。
- 条形可视化、配色、刻度走真机 DoD。

## 真机 DoD

成绩页 → 展开学期 → 展开某门课：① 三条排名百分位条按 前X% 填充、档位配色对、附原文+人数；② 成绩对比条 平均/你/最高 刻度位置对、你高亮；③ 等级制课程(score 非数字) 成绩对比退回文本不崩；④ 无 cjfx 详情的课显「无详情」。

## 不做 / 保留

- 不做估计分布曲线（方案 C）。
- 不动概览层既有图表（GPA 趋势 / 成绩分布）、不动 AI 分析 / 分享卡。
- 不改数据层 / DB / 网络 / cjfx 解析。

## 影响面

新建 `CourseDetailViz.kt`、`GradeVizMath.kt`、`GradeVizMathTest.kt`；改 `TermGradeSection.kt`（展开块换 `CourseDetailViz(c)`）；删 `GradeDetailLines.kt`、`GradeDetailLinesTest.kt`。复用主题色 Phosphor/Amber/Carmine/Rule/Foam/FoamDim/FoamMute/Cyan。无 DB/网络改动。
