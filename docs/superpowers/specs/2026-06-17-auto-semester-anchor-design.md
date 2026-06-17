# 首启空课表自动定锚（学期起始日自动获取）设计

日期：2026-06-17
状态：已获批，待写实现计划

## 背景与问题

首次启动 app（无任何数据）时点进课程表（`CourseWeekGridScreen`），被强制先手动选择学期起始日期。根因：周网格渲染 `when` 把 `needsSemesterStart` 分支**短路在最前**，显示"请先到 Settings → 学期设置 设置学期起始日期"——把用户挡在了 `EmptyCoursesCta`（"从教务一键导入"按钮）**之前**，而恰恰是导入流程会自动定锚。

**已有设施**：BIT 导入链路 `ImportCoursesUseCase` 已经自动取学期起始日——登录 → `getCurrentTerm`(当前学期) → `getWeekAndDate(ZC=1)`(第1周七天日期) → `ResolveSemesterAnchorUseCase` 取周一(XQ=1)的 RQ 写入 `SemesterPreferences` → 再映射课程。整个导入 UI 无手设步骤。所以只需把首启空课表**引导到导入**即可自动定锚。

## 决策（已与用户确认）

- **范围**：仅方案 A（空课表把死胡同提示换成"一键导入"CTA + 安静的手动兜底）。不做独立"只取日期"动作、不做后台静默取。
- **跨学期旧锚点**：导入时按所选学期刷新——`ResolveSemesterAnchorUseCase` 改为总是用 BIT 周1日期覆盖（修"已设则沿用、跨学期用旧锚"的隐患）。

## ① 空课表（未定锚）引导导入，去死胡同

`CourseWeekGridScreen` 的 `ui.needsSemesterStart` 分支由纯文字提示改为 `SemesterSetupCta`（居中）：
- 标题 `# 课程表尚未初始化`（Foam）；提示 `从教务系统导入会自动获取学期起始日与课程`（FoamDim，居中）。
- 主按钮 `从教务系统一键导入`（Phosphor 实心）→ `onNavigateToImport`（已有回调；导入自动定锚+灌课程）。
- 副链接 `[手动设置学期起始日]`（FoamDim TextButton）→ 内联弹 `SemesterStartModal`（兜底，复用 AddCourse 同款，已自动归一到周一）。
- 屏内 `var showManualPicker`；`SemesterStartModal(onPicked = { 关闭 + vm.onSemesterStartPicked(it) }, onDismiss = { 关闭 })`。

未登录时点导入：沿用现有 IMPORT_WIZARD 的未登录拦截回跳，不在本屏处理。

## ② 课表 VM 改响应式观察起始日

`CourseWeekGridViewModel` 现在 `init` 里**一次性** `semester.startDate.first()` 读入 `bootstrap`——导入设好后，若 VM 仍在返回栈（未重建）则课表不刷新，仍显示"需设置"。改为响应式：

- `bootstrap` 由 `combine(semester.startDate, timetable.periods) { s, p -> Bootstrap(s, p) }.stateIn(...初值 null...)` 驱动；`uiState` 仍 `bootstrap.flatMapLatest { ... }`，起始日 null→非空 时自动从"需设置"切到渲染网格。
- 首次定位当前周：`init` 改为 `semester.startDate.first { it != null }`（挂起到首个非空）后按 `(today - start)/7 + 1` 设 `displayWeekIndex`（未设则一直等，随 VM 作用域取消，无害）。
- 新增 `fun onSemesterStartPicked(date: LocalDate)`：`viewModelScope.launch { semester.setStartDate(date) }`（响应式 bootstrap 会随之刷新）。
- `onCurrentWeek()` 仍用 `bootstrap.value?.semesterStart`（`StateFlow` 有 `.value`，OK）。

## ③ 导入按所选学期刷新锚点

`ResolveSemesterAnchorUseCase.invoke(week1Days)` 删除"已设则沿用"短路（首行 `semesterPrefs.startDate.first()?.let { return it }`），改为**总是**取 `week1Days` 中 `weekday==1` 的 RQ、`setStartDate` 覆盖并返回。该用例仅被导入调用，导入本就取了所选学期(`pickedTermCode`)的周1日期，故等价于"按所选学期刷新"。`semesterPrefs` 不再读 `startDate`，移除 `kotlinx.coroutines.flow.first` 导入，更新 doc 注释。

## 测试

- `ResolveSemesterAnchorUseCaseTest`：test 1 由"`respects existing startDate`"改为"**已设也按 BIT 周1覆盖**"（断言返回 BIT 周一日期 + `coVerify(exactly=1){ setStartDate(...) }`）；test 2/3（未设取周一、乱序取周一）保留。
- 周网格 VM 响应式刷新、CTA 交互以真机 DoD 验（无现成 VM 测试，纯展示+网络流，不强加）。
- 真机 DoD：清数据首启 → 课表 → 见 `SemesterSetupCta`（非死胡同）→ 一键导入 → 自动定锚 + 课程、课表**即刻渲染**；手动兜底（`[手动设置学期起始日]`→日期选择→课表渲染）可用；跨学期再导入按新学期周1刷新锚点。

## 不做 / 保留

- 不做独立"只取日期不导课"按钮（方案 B）、不做后台静默取（方案 C）。
- `SemesterStartModal`/`SemesterSettingsScreen`/`AddCourseScreen` 既有手设路径不动。
- 无 DB schema / 网络接口改动（复用 `getWeekAndDate` 与导入链路）。

## 影响面

改 `CourseWeekGridScreen.kt`（needsSemesterStart 分支 + SemesterSetupCta + SemesterStartModal 兜底 + 状态/导入）、`CourseWeekGridViewModel.kt`（bootstrap 响应式 + onSemesterStartPicked + init 定位）、`ResolveSemesterAnchorUseCase.kt`（总是覆盖）；更新 `ResolveSemesterAnchorUseCaseTest.kt`。复用 `SemesterStartModal`/现有导入回调。
