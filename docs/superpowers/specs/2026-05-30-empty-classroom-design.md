# 空教室查询 — 设计

## 背景与目标

加「空教室查询」模块,帮学生快速找到当前/指定时段空闲的教室自习。实现原理参考 BIT101(同一 BIT ehall 数据源),但在**易用性、功能性、交互性**上做创新提升,且 **UI/交互要精致**——复用本项目已有的信息卡 + CRT 终端美学,不能简陋。

## 数据源(已确认,非盲猜)

来自 BIT101 的 Android/iOS/Python 三个开源实现交叉验证(非假设)。数据在 **BIT ehall `kxjasbyMobile`**(host `jxzxehallapp.bit.edu.cn`),**复用本 App 现有的 `apiClient.jwapp` + CAS SSO + 共享 cookie jar + wdkbby warm-up**(`getAppConfig/wdkbby-5959167891382285.do` + `i18n.do?appName=wdkbby`),**零新认证基建**。

三个调用:
- 校区:`GET jwapp/sys/kxjasbyMobile/modules/jxllb/ggzdpx.do?dicCode=48682&SFSY=1&order=+DM` → `datas.ggzdpx.rows: [{DM, MC}]`
- 教学楼:`GET jwapp/sys/kxjasbyMobile/modules/jxllb/cxjxl.do?XXXQDM={campusCode}`(省略=全部)→ `datas.cxjxl.rows: [{JXLMC, JXLDM, XXXQDM, XXXQDM_DISPLAY}]`
- 占用:`POST jwapp/sys/kxjasbyMobile/kxjasbyController/cxkxjasqk.do`,form `XQDM`(学期尾段,如 `2`)/`JXLDM`(楼代码)/`RQ`(日期 yyyy-MM-dd)/`XNXQDM`(如 `2024-2025-2`)/`XNDM`(如 `2024-2025`)→ `datas.cxkxjasqk.rows: [{JASMC, ZYJC}]`

`ZYJC` = 逗号分隔的**忙**节次串(如 `"1,2,3,7,8"`),`null` = 全天空。学期三参由现有课表学期串 `"2024-2025-2"` 派生(整串=XNXQDM、去尾段=XNDM、尾段=XQDM)。节次 1–13 的钟点用固定时间表映射(1=08:00、6=13:20、11=18:30 … 详见实现)。

**空闲算法**:每教室 `Free = (1..13) − 忙节次集`;合并连续节次成区间显示(`1~4, 6, 8~10`);用时间表把节次边界映射 `LocalTime` 算「现在空 / 下次忙 / 下次空」,带可配置「空闲分钟阈值」(默认 5 分钟,课间小空档不算)。

## 架构

- **data**:`BitJwappService` 加 3 端点(校区/楼/占用)+ DTO(`CampusDto/BuildingDto/RoomOccupancyDto` + 各 Response wrapper);`EmptyRoomRepository`(拉取 + 缓存)。
- **domain**:models `Campus / Building / RoomFreeSlots(roomName, freeRanges, busyPeriods, freeNow, nextChangeAt)`;`ComputeFreeSlotsUseCase`(ZYJC → 空闲段 + 现在空/下次变化,纯函数易测);`PeriodTimeTable`(1–13 节 ↔ LocalTime)。
- **feature/emptyroom**:`EmptyRoomViewModel` + `ui/EmptyRoomScreen`(+ 子组件 `RoomCard`、`OccupancyGrid`、`TimeAxisSlider`、`SmartHeader`)。
- **入口**:profile 网格加第 5 张卡「空教室」(图标如 `◳`);2×2 网格扩成 5 卡(2×2 + 居中 1,或 3 列布局)。

## 页面 UI(列表为主 + 点开网格;精致优先)

`EmptyRoomScreen`(CRT 背景 `scanLines()/vignette()`):
1. **智能头部 `SmartHeader`**:两枚醒目按钮——「⚡ 现在去自习」(扫当前校区,按连续空闲时长排)、「↪ 下节课后去哪」(课表联动)。视觉重心,Phosphor 强调。
2. **筛选条**:校区 · 教学楼(含「全校区」)· 日期 · 条件 chip(接下来 N 小时空 / 持续到 X 点)。终端风 chip,选中 Phosphor。
3. **结果列表**:空闲优先排序。每个 `RoomCard`(直角边框 + Deep 底,呼应考试/作业信息卡):教室名 + 空闲段 `1~4, 6` + 状态徽章「现在空 · 还能空 2.5h」(Phosphor)/「14:10 后空」(Cyan)/「全天忙」(灰)。
4. **点开 → `OccupancyGrid`**:该教室 1–13 节 × 占用 的可视化条(忙=Carmine/Amber 着色,空=Phosphor 淡底,带节次钟点),一眼看占用分布;CRT 风,无圆角。
5. **时间轴 `TimeAxisSlider`**:顶部可拖动滑块选时刻(08:00–21:00),列表按该时刻「是否空闲」实时重排/重染——交互核心,要流畅。

**UI 质量硬要求**:全程复用项目信息卡设计语言(直角边框、Deep/Void 层次、Foam/FoamMute/FoamDim 三级文字、Phosphor/Cyan/Amber/Carmine 语义色)、CRT 背景、等宽 MapleMono 字体;空闲优先 + 状态徽章 + 网格着色 + 时间轴动效要有"精致感",不是裸列表。参考已落地的考试页/作业页信息卡与课程表周视图网格。

## 四创新落点

1. **智能「现在去自习」**:一键扫当前校区所有楼(并发拉 + 缓存),聚合「现在空」教室,按「能连续空多久」降序——免逐级选。
2. **跨楼 / 全校区 + 条件筛**:教学楼可选「全校区」→ 并发拉该校区所有楼并合并;条件筛「接下来 N 小时空」「持续到 X 点空」「座位数 ≥ K」(\*座位筛见 §F12)。
3. **可视化网格 + 时间轴**:点开教室看节次×占用网格;顶部时间轴滑块拖动看任意时刻的空教室。
4. **与个人课表联动「下节课后去哪」**:读 `TimelineDao` 今天的 COURSE,算「当前这节后 / 下一个空档」的时段,自动预填查询并直接给出该时段空教室。

## 登录 / 缓存 / 性能

- **登录**:首次查询走 CAS 登录(复用 ssoLogin);未登录显示引导(同考试/作业的 `onNeedLogin → login(next)`),profile 卡未登录可点(进页面后引导)。
- **缓存**:占用按 `(楼, 日期)` 缓存(当天稳定,到下一节次边界或换日失效);校区/楼列表长缓存(不常变)。全校区扫描并发请求 + 缓存,避免每次重拉。
- **性能**:全校区可能十几栋楼 → 并发 + 进度反馈 + 缓存命中即时返回;单楼查询一次请求。

## 真机 F12 待确认(DoD,像 P9 抓一次确认)

endpoint 路径/参数/响应外层已由 BIT101 源码三端确认;需真机确认的是细节,不阻塞主路径:
1. `ZYJC` 边界格式(是否纯整数 csv、`null` 处理)。
2. `cxkxjasqk.rows` 是否含**座位数 / 教室类型**字段 → **决定「座位筛」做不做**;抓不到则首版不含座位筛。
3. `kxjasbyMobile` 是否需自己的 appId/getAppConfig(三端只 warm wdkbby,真机验证够不够)。
4. magic 常量 `dicCode=48682` / `SFSY=1` 当前学期是否仍有效。
5. 大楼是否分页(mobile 端 BIT101 未见分页,确认一次返回全部)。

## profile 入口

profile 核心网格加第 5 卡「空教室」(图标 `◳`,无状态或「现在空 N 间」),点击进 `EmptyRoomScreen`;`onOpenEmptyRoom` 经 `AppNavHost` 导航到新路由 `NavRoutes.EMPTY_ROOM`。

## 错误处理与边界

- 网络/会话失败:显示错误(复用错误展示风格),可重试;不静默清缓存。
- 某楼无数据/全忙:列表显示「该楼当前无空教室」,不报错。
- 学期/日期非教学周:占用可能全空或空响应,提示。
- 课表联动但今天无课:「今天无课,直接选时段查询」。

## 测试

- `ComputeFreeSlotsUseCase`:ZYJC(含 null / 边界)→ 空闲段、合并连续、现在空/下次变化、空闲阈值。
- `PeriodTimeTable`:节次 ↔ LocalTime。
- `EmptyRoomViewModel`:智能排序(连续空闲时长)、条件筛(N小时/持续到X)、全校区合并、课表联动空档计算、登录引导。
- `EmptyRoomRepository`:多楼合并、缓存命中/失效。

## 实现分阶段(plan 拆 task)

1. 数据层:端点 + DTO + 时间表 + ComputeFreeSlots + repo(单楼)。
2. 核心查询页:校区/楼/日期 → 空闲优先列表 + 点开网格(信息卡 + CRT)。
3. 智能「现在去自习」+ 条件筛 chip。
4. 全校区并发 + 缓存 + 时间轴滑块。
5. 课表联动「下节课后去哪」。
6. profile 第 5 卡入口 + 真机 DoD(F12 确认 + 座位筛取舍)。

## 开放问题

无 —— 数据源、4 创新、入口(profile 卡)、无 GPS(按空闲时长排)、列表+网格展示、UI 精致度均已确认。座位筛是已知的 F12 条件项(抓到字段才做),非开放问题。
