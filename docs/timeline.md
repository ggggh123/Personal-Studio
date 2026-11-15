# 日程与提醒

[返回项目入口](../README.md)

## 数据模型与显示

课程、待办、自定义日程和考试共用 [`TimelineItemEntity`](../app/src/main/java/com/example/personal_studio/data/local/db/entity/TimelineItemEntity.kt)。`type` 决定时间线样式与提醒档位，`sourceType` 区分手动、教务课表、乐学 DDL 和考试来源，不能用类型代替同步来源筛选。

课程按每次上课展开为独立行，同门课程用 `seriesId` 关联，并保留周次、星期和起止节次。这样日视图、周视图、单次修改／删除及提醒都使用真实时间戳，代价是修改课程规则需要更新多行。当前没有通用 RRULE 引擎。

手动课程由 [`AddCourseSeriesUseCase`](../app/src/main/java/com/example/personal_studio/domain/timeline/AddCourseSeriesUseCase.kt) 按连续周范围和多个星期展开；导入课程按学校 `SKZC` 位图展开，支持不连续周。课程列表可修改整组名称、教师、地点、学分和备注；修改系列上课时段需要删除后重建。具体表单和单次操作入口在 [`feature/timeline`](../app/src/main/java/com/example/personal_studio/feature/timeline)。

[`ComputeBubbleStateUseCase`](../app/src/main/java/com/example/personal_studio/domain/timeline/ComputeBubbleStateUseCase.kt) 根据类型、起止时间和完成状态生成临近／进行中／已过／逾期等状态；颜色和动效属于显示层，不写入完成状态。待办的完成、考试的“已考”均是本地勾选，不等于学校系统确认提交或出席。

## 学期与作息表

[`SemesterTimeMapper`](../app/src/main/java/com/example/personal_studio/core/util/SemesterTimeMapper.kt) 将“学期第一周周一 + 周次 + 星期 + 节次表”转成实际时间。默认 [13 节作息表](../app/src/main/java/com/example/personal_studio/core/util/DefaultTimetable.kt)可在设置中调整。

课表导入请求 `cxzkbrq.do` 时带 `ZC=1`，取 `XQ=1` 的 `RQ` 作为第一周周一；[`ResolveSemesterAnchorUseCase`](../app/src/main/java/com/example/personal_studio/domain/bitimport/ResolveSemesterAnchorUseCase.kt) 每次都会保存该日期，覆盖旧锚点和手工设定。此步骤发生在预览确认之前，所以取消导入不会回滚锚点。

没有锚点时周课表提供导入引导和手工设置入口。[周课表 ViewModel](../app/src/main/java/com/example/personal_studio/feature/timeline/vm/CourseWeekGridViewModel.kt)持续观察锚点变化，避免导入后仍停留在旧空态。作息表修改会重算未来课程时间并重排受影响提醒；仍被未来课程引用的末尾节次不能直接删除。

课程展开及日视图主要使用设备时区；考试解析和乐学非 UTC 日期使用 `+08:00`。跨时区使用不是已经统一处理的场景，不能假设所有学校时间都按相同策略换算。

## 学校数据写入规则

认证和取数见[学校系统接入](school-integration.md)。写入时有三种不同策略：

| 来源 | 当前策略 | 对本地修改的影响 |
| --- | --- | --- |
| 课表 | 确认后删除锚点起 25 周范围内的 `IMPORTED_PORTAL`，再写入新展开行 | 保留手动行；重新导入会覆盖该范围内对导入课程的本地修改，并重新分配系列 |
| 乐学 DDL | 按 UID 更新／插入，删除本次订阅结果中消失的 UID | 保留匹配条目的 `isDone/doneAt`；截止变更静默更新 |
| 考试 | 按“学期、课程、开始时间”构成的 UID 更新／插入，删除消失项 | UID 相同则保留完成状态；考试时间改变会成为新 UID |

实现分别在 [ReplaceImportedCoursesUseCase](../app/src/main/java/com/example/personal_studio/domain/bitimport/ReplaceImportedCoursesUseCase.kt)、[ReplaceImportedDdlUseCase](../app/src/main/java/com/example/personal_studio/domain/bitddl/ReplaceImportedDdlUseCase.kt)、[ReplaceImportedExamUseCase](../app/src/main/java/com/example/personal_studio/domain/bitexam/ReplaceImportedExamUseCase.kt)。这些批量写入当前没有统一事务，不能宣称中途失败会完整回滚。

单独导入课表需要预览确认，空课表不覆盖旧数据；一键同步会自动确认预览。有效的空 DDL／考试结果则会删除该来源的现存条目，因此区分“真实空结果”和“会话失效／响应异常”必须在替换前完成。

## 提醒调度

单项提醒使用 [`ScheduleRemindersUseCase`](../app/src/main/java/com/example/personal_studio/domain/timeline/ScheduleRemindersUseCase.kt) 的 AlarmManager，网络轮询和批量重排使用 WorkManager，两者职责不同。

| 类型 | 当前提醒档位 |
| --- | --- |
| 课程 | 开始前 10 分钟 |
| 待办／乐学 DDL | 截止前 1 天、2 小时、30 分钟，以及截止时的逾期提醒 |
| 自定义日程 | 开始前 30 分钟 |
| 考试 | 开始前 1 天、2 小时、30 分钟，无逾期提醒 |

已过的触发时间不再补发。有精确闹钟权限时用 `setExactAndAllowWhileIdle`，无权限或调度时权限被撤销则降级为 `setAndAllowWhileIdle`，降级后不保证准点。通知还受系统通知权限和应用分类开关影响，考试复用待办开关。

每个 PendingIntent 的 data URI 包含 `itemId/minBefore/isOverdue`，保证不同事项及档位互不覆盖；只改 extras 不足以建立唯一性。[ReminderAlarmReceiver](../app/src/main/java/com/example/personal_studio/core/workers/ReminderAlarmReceiver.kt) 收到闹钟后重新查数据库和开关，已删除或完成的事项不会通知。

手动新增课程会逐条排提醒；完成／删除等交互负责取消相应提醒。应用 Activity 启动、开机及 DDL／考试同步会请求批量重排，但 [RescheduleRemindersWorker](../app/src/main/java/com/example/personal_studio/core/workers/RescheduleRemindersWorker.kt) **仅扫描未来 24 小时内开始、未完成的事项**，并非全学期重建。当前课表导入用例也没有逐条调度；导入后、跨天、重启以及长时间不打开应用的覆盖需要继续完善和真机验证。
