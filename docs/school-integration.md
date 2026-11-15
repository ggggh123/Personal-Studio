# 学校系统接入

[返回项目入口](../README.md)

## 接入范围与入口

项目自行实现学校 CAS／WebVPN 请求，没有引入 BIT-Login SDK，也不使用 BIT101 社区后端账号。历史参考范围见[决策记录](decisions.md#参考项目与验证边界)。

| 系统 | 用途 | 当前代码中的服务地址／入口 |
| --- | --- | --- |
| CAS | 统一身份认证、签发服务 ticket | `https://sso.bit.edu.cn/cas/login` |
| WebVPN | 校外代理网关会话 | `https://webvpn.bit.edu.cn/` |
| 教学中心 jwapp | 课表、考试、空教室 | `jxzxehallapp.bit.edu.cn`；认证预检走 `jxzxehall.bit.edu.cn` |
| jwms／jsxsd | 成绩列表与单课详情 | CAS 注册 service 为 `http://jwms.bit.edu.cn/` |
| 乐学 Moodle | 日历订阅与作业 DDL | `https://lexue.bit.edu.cn/` |

精确 URL、代理编码和可信跳转检查以 [BitAuthSession](../app/src/main/java/com/example/personal_studio/data/network/bit/BitAuthSession.kt)、[BitUrlsConfig](../app/src/main/java/com/example/personal_studio/data/network/bit/BitUrlsConfig.kt) 为准。WebVPN 将目标 host 编入路径；Retrofit 使用相对业务路径，不能用以 `/` 开头的路径冲掉代理前缀。乐学在当前两种模式下都使用公网直连。

## 登录与会话

### 原生认证链

[`SsoLoginUseCase`](../app/src/main/java/com/example/personal_studio/domain/bitimport/SsoLoginUseCase.kt) 为教学中心、成绩和乐学提供分别的登录入口：

1. 按账号准备共享 Cookie。教学中心先做不带 Cookie 的公开认证预检，获取带本次 nonce 的 service；成绩和乐学使用各自注册 service。
2. 直连 CAS 请求该 service 的 ticket。已有 TGC 能签发 ticket 时直接复用；否则解析 `login-page-flowkey` 和 `login-croypto`，按 [CasAesCrypto](../app/src/main/java/com/example/personal_studio/core/util/CasAesCrypto.kt) 的协议加密并提交表单。密钥需先 Base64 解码，算法是学校要求的 AES-ECB，不是旧计划中的 CBC。
3. 要求响应携带 ticket，且回调 origin／path 与目标 service 匹配。HTTP 200、空响应、没有错误提示，均不能单独证明登录成功。
4. 教学中心／成绩使用 WebVPN 时，先另外申请网关 ticket 并建立网关会话，暂不兑换目标 ticket。成绩服务还需先访问根入口初始化其会话 Cookie。
5. 按选择的直连或代理路径兑换目标 ticket，再完成落地初始化；教学中心还访问课表入口。跳转限制在可信地址和最多 10 次之内，检查状态码及会话失败页面。

原生 HTTP 不执行网页 JavaScript。仅对独立、完整的字面量跳转脚本做受限跟随；函数体、点击事件和条件中的 `location` 赋值不能当成立即跳转。解析与错误页识别在 [BitAuthPages](../app/src/main/java/com/example/personal_studio/data/network/bit/BitAuthPages.kt)。

### 交互验证

[`BitLoginViewModel`](../app/src/main/java/com/example/personal_studio/feature/auth/BitLoginViewModel.kt) 在原生登录判定需要交互验证时，自动打开一次[学校官方网页弹窗](../app/src/main/java/com/example/personal_studio/feature/auth/ui/BitWebVerificationDialog.kt)。密码、短信及风控交互由学校页面处理，本应用没有原生短信验证码接口。

网页无主文档错误地到达干净的 HTTPS WebVPN 根入口后，触发原生再验证；其他落地页可使用“验证完成，继续”。**网页跳转或点击按钮本身不等于登录成功**，只有后续原生验证通过才保存凭据并回到目标功能。再次要求验证时停止自动弹窗，避免循环。

登录过程显示阶段、阻止重复提交并支持取消；同一次操作固定使用开始时的账号信息，只去掉学号首尾空白，保留密码字符。业务同步或后台任务遇到交互要求时返回提示，需要回到登录入口完成验证，不会在后台弹出网页。

### Cookie 与互斥

[`BitApiClient`](../app/src/main/java/com/example/personal_studio/data/network/bit/BitApiClient.kt) 是共享单例。前台、Worker、网页验证都必须遵守统一会话锁，从打开会话一直持有到关闭；关闭和释放要在 `finally` 中完成。仅让“一键同步”内部串行，不足以防止后台任务同时关闭客户端。

[`BitCookieJar`](../app/src/main/java/com/example/personal_studio/data/network/bit/BitCookieJar.kt) 以 `(domain, path, name)` 为键，新 Cookie 覆盖旧值，匹配请求使用标准 `Cookie.matches()`。原生请求通过 network interceptor 与 [CookieManager](../app/src/main/java/com/example/personal_studio/data/network/bit/BitBrowserSessions.kt) 双向同步，并及时写入响应 Cookie，使下一跳能立即使用。

每次 `close()` 会释放 Retrofit 实例和临时原生 Cookie，**保留浏览器 Cookie**。它们由学校有效期控制，切账号或退出登录才主动清除；没有“固定七天免短信”的客户端保证。校园页的“账户已保存”表示本地凭据存在，不表示所有服务在线，“上次连接”也不表示当前网络位置。

## 网络选择

[`ResolveNetworkModeUseCase`](../app/src/main/java/com/example/personal_studio/domain/bitimport/ResolveNetworkModeUseCase.kt) 使用[可取消的 jwms HEAD 探测](../app/src/main/java/com/example/personal_studio/data/network/bit/CampusReachabilityProbe.kt)：不跟随重定向，只接受 2xx，连接／读取各 2 秒、整个调用 3 秒。历史 `lastMode` 仅作为记录，自动选择会重新探测；当前登录界面已不提供手动网络切换。

前台 [NetworkFallback](../app/src/main/java/com/example/personal_studio/data/network/bit/NetworkFallback.kt) 只对连接级失败换另一方式尝试一次。密码错误、锁定、评教、解析异常或真实空数据不触发换网，防止掩盖原因或重复提交密码。Worker 对归类为 `Transient` 的结果做同样的另一方式尝试，再交给 WorkManager 退避。

该探测只反映 jwms 可达性，不能证明教学中心和乐学都必须经过 WebVPN；Wi-Fi／蜂窝也不能直接等同于校内／校外。当前仍是所有服务共用一个首选模式，尚未实现按服务、按当前网络缓存访问方式。

## 业务数据链路

### 课表

[`ImportCoursesUseCase`](../app/src/main/java/com/example/personal_studio/domain/bitimport/ImportCoursesUseCase.kt) 登录教学中心后调用 `getAppConfig`、`switchLang` 预热 `wdkbby`，再获取当前学期、第 1 周日期与课表。缺少预热曾导致登录成功后业务端点仍返回 403。

学期字典使用 `DM/MC`；课表行使用 `XNXQDM` 等另一组字段，不能照搬同名系统的 DTO。`cxzkbrq.do` 必须传 `ZC=1` 才获得第一周日期；课程按 `SKZC` 位图展开。预览确认、25 周覆盖范围与学期锚点副作用见[日程写入规则](timeline.md#学校数据写入规则)。

### 成绩与分析

[`SyncGradesUseCase`](../app/src/main/java/com/example/personal_studio/domain/bitgrades/SyncGradesUseCase.kt) 建立 jwms 会话后，POST `jsxsd/kscj/cjcx_list`，提交 `kksj/kcxz/kcmc/xsfs=all`。数据源是 HTML，不是早期假设的 ehall `cjcx` JSON 接口。

同步在解析前检查 HTTP 状态、登录／评教拦截页和 `table#dataList` 是否存在，再交给 [JsxsdGradeParser](../app/src/main/java/com/example/personal_studio/domain/bitgrades/JsxsdGradeParser.kt)。找不到表属于异常；真实空表也不执行前台全量覆盖。单课 `cjfx` 详情为可选补充，详情失败保留该课基本成绩，不阻断整个同步。

成绩列表没有直接提供全部绩点字段，由 [BitGpaConverter](../app/src/main/java/com/example/personal_studio/core/util/BitGpaConverter.kt) 换算，再经 [ComputeGpaUseCase](../app/src/main/java/com/example/personal_studio/domain/bitgrades/ComputeGpaUseCase.kt) 按学分加权。当前百分制算法为低于 60 分记 0，否则 `4 − 3 × (100 − 分数)² / 1600`；通过／免修等无法计入 GPA 的项目返回 null 并跳过。等级制和均分代表值以转换器为准。

界面还提供目标 GPA 反推、假设绩点预测、成绩分享卡和 AI 学情报告。AI 摘要不含姓名／学号，但会把课程及成绩信息发给用户配置的模型服务，不能称为完全离线分析。

“专业前 p%”中 p 越小越靠前；整体专业百分位和同学平均绩点是本地估计，不是学校发布的总排名。模型假设、非线性换算与区间含义见[成绩统计决策](decisions.md#成绩统计的解释边界)。

### 考试

当前考试入口为 `wdksapMobile/*default/index.do`，业务请求为 POST `wdksapMobile/modules/ksap/cxxsksap.do`，表单为 `XNXQDM` 和 `*order=-KSRQ`。旧 jsxsd 考试 HTML 与旧 `studentWdksapApp` 路径均不代表现状。

[`SyncExamsUseCase`](../app/src/main/java/com/example/personal_studio/domain/bitexam/SyncExamsUseCase.kt) 取当前学期，预热考试入口后取数；[ExamRowMapper](../app/src/main/java/com/example/personal_studio/domain/bitexam/ExamRowMapper.kt) 解析课程、时间、地点及座位，按 `+08:00` 换算时间并写入 Timeline 的 `EXAM`。仅有日期时退化到当天零点；不能解析的行会被跳过。

### 乐学作业

[`GenerateLexueIcalUrlUseCase`](../app/src/main/java/com/example/personal_studio/domain/bitddl/GenerateLexueIcalUrlUseCase.kt) 首次登录乐学，提取 `sesskey`，提交日历导出表单并提取含 token 的订阅 URL，保存到 DdlSyncPrefs。这个 URL 本身带访问能力，不要放进日志或文档。

[`SyncAssignmentsUseCase`](../app/src/main/java/com/example/personal_studio/domain/bitddl/SyncAssignmentsUseCase.kt) 优先直接读取已有订阅，响应不是 VCALENDAR 时重新登录派生一次，再失败才报错。无需每次读取都重新认证。

[`LexueIcalParser`](../app/src/main/java/com/example/personal_studio/domain/bitddl/LexueIcalParser.kt) 处理折行、TEXT 转义、UTC 时间及本地／全天日期，取 UID、标题、描述、课程和 `DTSTART`；非 UTC 日期按 `+08:00`。这是面向乐学 feed 的有限解析器，不支持所有 RFC 5545 规则或任意时区。单条缺 UID／日期会跳过，合法日历但条目解析失败仍可能得到空列表，这与真实空订阅目前并未完整区分。

DDL 是日历元数据；“完成”仅在本地记录，没有查询 Moodle 的实际作业提交状态。

### 空教室

[`EmptyRoomRepository`](../app/src/main/java/com/example/personal_studio/domain/emptyroom/EmptyRoomRepository.kt) 登录教学中心并预热 `wdkbby`，读取校区、教学楼和 `kxjasbyController/cxkxjasqk.do` 的占用数据；多个教学楼在同一个已打开会话内并发获取。

[`ComputeFreeSlotsUseCase`](../app/src/main/java/com/example/personal_studio/domain/emptyroom/ComputeFreeSlotsUseCase.kt) 把 `ZYJC` 忙节次转换为 1–13 节的空闲区间，结合用户作息表计算“现在空／空到何时”等状态。结果不是教室实时门禁或实际占座信息。

缓存只存原始占用行，查询时重新计算相对时间；当前键为“楼代码 + 日期”，没有旧设计承诺的到节次边界自动失效，也没有 TTL 或学期／账号隔离。部分缺失响应仍以空列表处理，不能把空教室结果当作所有学校接口已完成严格响应校验的例子。

## 一键同步与后台轮询

[`SyncAllUseCase`](../app/src/main/java/com/example/personal_studio/domain/bitimport/SyncAllUseCase.kt) 按 **课程 → DDL → 考试 → 成绩** 串行执行，各自开会话、登录、同步、关闭；单项失败继续后续项。课程预览在此自动确认；当前“重试”会重跑全部来源，而非只跑失败项。完成或跳过会设置首次同步标记。

成绩和 DDL 轮询使用 WorkManager，默认关闭，可选 6／12／24 小时；成绩默认 6 小时，DDL 默认 12 小时。任务要求网络已连接，周期执行不保证精确时刻。应用启动和开机会按配置恢复调度。

| 行为 | 成绩轮询 | DDL 轮询 |
| --- | --- | --- |
| 变化识别 | 学期、课程、考试性质、成绩构成签名，改分也可成为新增变化 | UID 集合差异；改截止时间只更新，不算新作业 |
| 首次运行 | 建立通知基线，不全量写入成绩库、不通知 | 写入 Timeline 并建立基线，不通知 |
| 后续运行 | 只为新增变化补详情并 upsert，保留未变化课程的现有详情 | 每次按来源更新 Timeline，再通知新增 UID |

实现入口：[GradePollWorker](../app/src/main/java/com/example/personal_studio/core/workers/GradePollWorker.kt)、[DdlPollWorker](../app/src/main/java/com/example/personal_studio/core/workers/DdlPollWorker.kt)。无凭据时停轮；密码错误／锁定会清凭据并停止该轮询；需要网页验证或解析失败会停轮并通知；未评教只记录状态，保持启用。瞬时网络失败交给 WorkManager 指数退避，避免连续认证请求。

## 当前边界

- 每个业务入口仍会重新走目标服务认证链；可复用 CAS Cookie，但没有独立的“服务会话仍有效就直接执行业务”入口。
- 部分 CAS 5xx 等临时故障仍进入通用失败／解析失败，可能停止后台轮询。更细的临时错误分类及验证后恢复原任务尚未完成。
- 乐学 token、业务缓存与已下载数据没有完整账号隔离；有效空结果与解析丢失的保护在不同功能之间也不一致。
- 学校是否需要短信、多久免验证由服务端决定。不能从一次成功登录或单元测试通过推导出长期免验证、全服务可用。

## 排障顺序

先确定失败发生在“探测 → CAS ticket → WebVPN → 服务回调 → 模块预热 → 业务取数 → 解析 → 写入”的哪一步，再修改对应实现。只记录脱敏后的阶段、状态码、host/path、响应类型和 ticket 是否存在，不记录密码、Cookie、完整查询参数、短信或订阅 URL。

对 200 响应先识别是业务数据、登录页还是错误页；对 Retrofit 非 2xx 同时检查 `errorBody()`，不要把 `body()==null` 当成功。用真实且脱敏的结构补测试，再在真实校园网／移动数据下验证；历史验收范围见[决策记录](decisions.md#参考项目与验证边界)。
