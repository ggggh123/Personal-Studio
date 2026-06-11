# 成绩详情增强（课程展开显示更多信息）

日期：2026-06-11
状态：已获批，待写实现计划

## 背景与目标

成绩查询页点击某门课程会展开一个详情，目前只显示 **平均分 · 班级% · 专业%** 三项。对标 BIT101，希望展示更丰富的信息（课程类型、修学人数、最高分、绩点、全校排名等）。

**关键发现（调研结论）**：大部分目标字段「已经抓到并存储，只是 UI 没显示」——这主要是展示缺口，不是数据缺口。

| 字段 | 来源 | 现状 |
|---|---|---|
| 课程类型/性质（必修/选修…） | 主成绩列表 `category` | 已抓已存，**未显示** |
| 修学人数（学习人数） | cjfx 详情 `courseStudyCount` | 已抓已存，**未显示** |
| 最高分 | cjfx 详情 `courseMaxScore` | 已抓已存，**未显示** |
| 绩点 | 本地换算 `gradePoint` | 已有，**未显示** |
| 平均分 / 班级% / 专业% | cjfx 详情 | 已显示 |
| 班级人数 / 专业人数 | cjfx 详情 | **未解析** |
| 全校百分位（在所有学生中占 X%） | cjfx 详情 | **未解析** |

cjfx 成绩分析页（真机验证）是一串 `<td>标签：值</td>`，已确认含：班级人数 / 专业人数 / 学习人数 / 平均分 / 最高分 / 本人成绩 / 本人成绩在班级中占 / 在专业中占 / 在所有学生中占。

## 范围（已确认：方案 B）

显示「已抓到的」+ 补抓 3 个新字段（班级人数 / 专业人数 / 全校百分位）。

不做：课程编号（噪音）；不重排成大表（用紧凑分组三行，贴现有 CRT 风格）。

## 设计

### ① 数据层：解析器 + 存储 + 模型

**`JsxsdDetailParser.DetailInfo`** 增 3 个字段，从已有的 `<td>标签：值</td>` map 里多取：
- `classSize: Int?` ← `find("班级人数")`，取其中数字
- `majorSize: Int?` ← `find("专业人数")`，取其中数字
- `schoolRankText: String?` ← `find("所有学生中占", "全校中占", "全校")`（现在这个 46% 无人取）

`majorRankText` 保持现状（`find("专业中占", "专业排名", "年级")`）——「年级」旧 fallback 不动，全校只匹配「所有学生中占 / 全校」，二者不撞。

**`GradeEntryEntity`**（Room）增 3 列：`classSize: Int?`、`majorSize: Int?`、`schoolRankText: String?`。dev 库按惯例可销毁重建、不写数据保留迁移（`fallbackToDestructive` 已启用）。

**`GradeItem`**（领域模型，UI 直接消费）增对应 3 字段。entity→GradeItem 的映射处补上这 3 个字段。

**enrich（两处都改，保持一致）**：
- 前台 `SyncGradesUseCase` 的 enrich（对每门课并发拉 cjfx 详情）：把 `DetailInfo` 的 3 个新字段拷进 entity。
- 后台 `GradePollWorker.enrichOne`（仅对本次新增课增量拉）：同样拷 3 个新字段。

> 注意：这 3 个新字段需**重新同步一次**才会有值（旧库行只有原来的 4 个详情字段）。课程类型来自主列表、与同步无关、一直都有。

### ② UI 层：`TermGradeSection` 分组三行

把当前单行 `parts` 换成最多 3 行；每行内 null 的项省略、整行全 null 则不出该行、所有都空才显示「无详情」：

```
必修 · 绩点 4.0
平均 78.7 · 最高 100 · 修学 1178人
班级 67%(18人) · 专业 63%(32人) · 全校 46%
```

- 第 1 行 课程属性：`category` + `绩点 gradePoint`（绩点保留 1 位小数）
- 第 2 行 成绩对比：`平均 courseAvg` · `最高 courseMaxScore` · `修学 courseStudyCount 人`
- 第 3 行 排名：`班级 classRankText(classSize人)` · `专业 majorRankText(majorSize人)` · `全校 schoolRankText`
- 人数括注仅在对应人数非 null 时附加。
- 沿用 Cyan + labelMedium，缩进同现状（start = 28.dp）。

### ③ 错误 / 空值处理

- 任一字段 null → 该项省略；整行所有项 null → 不渲染该行。
- 三行全空（未 enrich 且无 category）→ 显示「无详情」（同现状）。
- 未 enrich 的行：通常仍有 category（课程属性行可见），成绩对比/排名行缺席——合理降级。

### ④ 测试

- `JsxsdDetailParserTest`：现有真机样例 HTML 已含「班级人数：18 人 / 专业人数：32 人 / 所有学生中占：46%」，加断言验证 `classSize=18`、`majorSize=32`、`schoolRankText="46%"`。
- entity→GradeItem 映射若有现成测试则补 3 字段断言。
- UI 纯展示，不强制单测（分组/省略逻辑可选加一个纯函数测试，如抽出 `buildDetailLines(item): List<String>` 便于测）。

## 影响面

改动文件（约 6 个 + 测试）：`JsxsdDetailParser`、`GradeEntryEntity`、`GradeModels(GradeItem)`、entity→GradeItem 映射处、`SyncGradesUseCase`(enrich)、`GradePollWorker`(enrichOne)、`TermGradeSection`(UI)；`JsxsdDetailParserTest`。低风险、无网络协议改动。
