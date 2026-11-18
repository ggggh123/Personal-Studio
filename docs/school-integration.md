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
| 新乐学（畅课 TronClass） | 作业 DDL 与提交状态 | `https://zy-eclass.bit.edu.cn/`；认证经 `zy-identity.bit.edu.cn`（Keycloak）转到 CAS |

精确 URL、代理编码和可信跳转检查以 [BitAuthSession](../app/src/main/java/com/example/personal_studio/data/network/bit/BitAuthSession.kt)、[BitUrlsConfig](../app/src/main/java/com/example/personal_studio/data/network/bit/BitUrlsConfig.kt) 为准。WebVPN 将目标 host 编入路径；Retrofit 使用相对业务路径，不能用以 `/` 开头的路径冲掉代理前缀。乐学和新乐学在当前两种模式下都使用公网直连。延河课堂门户 `aita.yanhekt.cn` 顶栏的“教学管理（新乐学）”就指向 zy-eclass，门户本身不放作业。

## 登录与会话

### 原生认证链

[`SsoLoginUseCase`](../app/src/main/java/com/example/personal_studio/domain/bitimport/SsoLoginUseCase.kt) 为教学中心、成绩、乐学和新乐学提供分别的登录入口：

1. 按账号准备共享 Cookie。教学中心先做不带 Cookie 的公开认证预检，获取带本次 nonce 的 service；成绩和乐学使用各自注册 service。新乐学从 `/user/courses` 顺着 zy-identity 的跳转走到 CAS，取其中带本次 `state` 的 broker service（地址写作 `/cas//login`，判断 CAS 时会合并重复斜杠）；若 zy-eclass 直接返回页面，说明会话仍有效，不再认证。
2. 直连 CAS 请求该 service 的 ticket。已有 TGC 能签发 ticket 时直接复用；否则解析 `login-page-flowkey` 和 `login-croypto`，按 [CasAesCrypto](../app/src/main/java/com/example/personal_studio/core/util/CasAesCrypto.kt) 的协议加密并提交表单。密钥需先 Base64 解码，算法是学校要求的 AES-ECB，不是旧计划中的 CBC。
3. 要求响应携带 ticket，且回调 origin／path 与目标 service 匹配。HTTP 200、空响应、没有错误提示，均不能单独证明登录成功。
4. 教学中心／成绩使用 WebVPN 时，先另外申请网关 ticket 并建立网关会话，暂不兑换目标 ticket。成绩服务还需先访问根入口初始化其会话 Cookie。
5. 按选择的直连或代理路径兑换目标 ticket，再完成落地初始化；教学中心还访问课表入口。跳转限制在可信地址和最多 10 次之内，检查状态码及会话失败页面。

原生 HTTP 不执行网页 JavaScript。仅对独立、完整的字面量跳转脚本做受限跟随；函数体、点击事件和条件中的 `location` 赋值不能当成立即跳转。解析与错误页识别在 [BitAuthPages](../app/src/main/java/com/example/personal_studio/data/network/bit/BitAuthPages.kt)。

### 交互验证

交互登录（登录页、校园页“重新验证”）时，[`BitLoginViewModel`](../app/src/main/java/com/example/personal_studio/feature/auth/BitLoginViewModel.kt) 把一个 [`CasPrompter`](../app/src/main/java/com/example/personal_studio/domain/bitimport/CasPrompter.kt) 交给登录链，学校要的额外步骤在 App 内的底部面板里完成（[CasVerifySheets](../app/src/main/java/com/example/personal_studio/feature/auth/ui/CasVerifySheets.kt)）：

- **短信二次验证**：密码提交后，CAS 返回带 `user-object-id` 和 `secondSmsLoginForm`／`second-auth-tip` 的页面（[`BitAuthPages.secondFactorPage`](../app/src/main/java/com/example/personal_studio/data/network/bit/BitAuthPages.kt)）。[`CasSmsChallenge`](../app/src/main/java/com/example/personal_studio/domain/bitimport/CasSmsChallenge.kt) 按学校页面的做法：`POST /cas/api/protected/sms/getPhoneNumberByUserId`（加密，取不透明的 `tel` 和脱敏 `maskTel`）→ `…/sms/publicNoToken/sendSmsCode` `{phone, businessNo: "0008"}` → 用户输入 → `…/sms/checkToken` `{phone, token, delete: false, trustDevice}`；通过后提交 `type=smsLogin` 表单（`password` 为验证码，带 `execution`、`trustDevice`），拿到 ticket 后照常兑换。412／8000068 表示上一条验证码仍有效，按返回的剩余秒数倒计时。
- **图形验证码**：交互登录在提交密码前先 `GET /cas/api/protected/user/findCaptchaCount/{学号}`；`captchaInvisible` 为真时显示 `captchaUrl`（data URL）里的图片，答案作为 `captcha_code`，`captchaPayload` 按 `croypto` 加密后作为 `captcha_payload`。这个检查失败或返回异常时照旧直接提交密码。
- **协议细节**（[`CasApi`](../app/src/main/java/com/example/personal_studio/data/network/bit/CasApi.kt)、[`CasCrypto`](../app/src/main/java/com/example/personal_studio/data/network/bit/CasCrypto.kt)，2026-11 对照学校 `cas-gateway` 前端脚本核实）：`protected` 路径带 `Csrf-Key`／`Csrf-Value`；需要加密的路径以服务端 `/gate/sso-extend/protected/api/dictconfig/get` 的 `URL_CRYPTO_DATA.SID` 列表为准（读不到时只加密查手机号这一项）。加密方式为随机 AES 密钥（Base64 文本）以 ECB 加密 JSON，密钥文本用学校脚本里的 RSA 公钥（PKCS#1）加密放进 `privateKey` 头，并带 `hasCrypto: true`；回复用同一密钥解密，可能嵌套两层。这些接口只在 CAS 源站调用，验证码不记日志、不保存。

原生流程处理不了的情况交给[学校官方网页弹窗](../app/src/main/java/com/example/personal_studio/feature/auth/ui/BitWebVerificationDialog.kt)：滑块等第三方验证码、首次修改密码、邮箱二次验证、查不到绑定手机、表单地址异常，以及用户在面板里点“改用学校网页验证”。这些都以 `CaptchaRequired` 结束原生登录，再自动打开一次网页。BIT-Login 只作接口参考（未声明开源许可，未复制代码）。

网页无主文档错误地到达干净的 HTTPS WebVPN 根入口后，触发原生再验证；其他落地页可使用“验证完成，继续”。**网页跳转或点击按钮本身不等于登录成功**，只有后续原生验证通过才保存凭据并回到目标功能。再次要求验证时停止自动弹窗，避免循环。

登录过程显示阶段、阻止重复提交并支持取消；同一次操作固定使用开始时的账号信息，只去掉学号首尾空白，保留密码字符。业务同步或后台任务没有 `CasPrompter`，遇到短信或验证码时返回“需要验证”，不会发送短信、不弹出界面，需要回到登录入口完成验证。

### Cookie 与互斥

[`BitApiClient`](../app/src/main/java/com/example/personal_studio/data/network/bit/BitApiClient.kt) 是共享单例。前台、Worker、网页验证都必须遵守统一会话锁，从打开会话一直持有到关闭；关闭和释放要在 `finally` 中完成。仅让“一键同步”内部串行，不足以防止后台任务同时关闭客户端。课表导入只在登录和读取期间持锁，读完即关闭并释放，再进入预览；用户停在预览页时不阻塞后台轮询和其他同步，确认后只写本地数据库。

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

### 新乐学作业

新乐学是畅课（TronClass）系统，没有日历订阅地址，只能登录后读接口。[`FetchEclassAssignmentsUseCase`](../app/src/main/java/com/example/personal_studio/domain/bitddl/FetchEclassAssignmentsUseCase.kt) 只读三类接口：

1. `GET api/my-semesters`，取 `is_active` 的学期（都没有时取 `sort` 最大的）。
2. `POST api/my-courses`，`conditions.semester_id` 限定本学期，分页。
3. 每门课 `GET api/courses/{id}/homework-activities?page=&page_size=100`，并发、分页。课堂小测也在这里。

时间是 UTC ISO-8601；取 `end_time`（缺省用 `deadline`），跳过未发布或无截止的条目。说明是教师填写的 HTML，转成纯文本。uid 为 `eclass-homework-<id>`。`api/todos` 只列未交且开放的作业，交完就为空，不能作为完整来源。

先直接读：学校网页 Cookie 仍有效（或 CAS 能直接签票）时不必认证。拿到的不是 JSON（被带到登录页）或 401／403 才调 `loginEclass`，再读一次；仍不是 JSON 则报解析失败。

两个平台在 [`SyncAssignmentsUseCase`](../app/src/main/java/com/example/personal_studio/domain/bitddl/SyncAssignmentsUseCase.kt) 的同一次会话里依次拉取，结果是按平台的 `DdlSyncOutcome`：

- 落库按平台进行，一个平台失败时不改它已有的作业；手动刷新只要一个平台成功就算完成，并写明哪个平台没同步上。
- 密码错误／账号锁定无论哪个平台发现都按账号问题处理；后台轮询在另一平台成功时不因单个平台的其他失败停轮，只记入结果说明。
- 新作业通知按平台建基线：某平台第一次同步不通知；没同步上的平台保留上次的 UID，恢复后不会误报。

新乐学返回本人是否已提交（`submitted`）。新作业已提交就直接记为完成；已有作业在“刚提交”（上次同步时未提交）时自动打勾并取消提醒。用户之后手动取消，不会再被勾上；系统从不自动取消完成。上次已提交的 uid 存在 DdlSyncPrefs。

### 空教室

[`EmptyRoomRepository`](../app/src/main/java/com/example/personal_studio/domain/emptyroom/EmptyRoomRepository.kt) 登录教学中心并预热 `wdkbby`，读取校区、教学楼和 `kxjasbyController/cxkxjasqk.do` 的占用数据；多个教学楼在同一个已打开会话内并发获取。

每次会话结束都会清空 Cookie，所以每打开一次会话就是一次完整的密码登录。为此空教室把登录次数压到最少（[`EmptyRoomViewModel`](../app/src/main/java/com/example/personal_studio/feature/emptyroom/EmptyRoomViewModel.kt)）：
- 校区和教学楼作为“目录”存在本机（[`EmptyRoomPrefs`](../app/src/main/java/com/example/personal_studio/data/local/datastore/EmptyRoomPrefs.kt)），30 天后或在选择面板里手动重新获取。获取时校区一次请求，教学楼用不带 `XXXQDM` 的 `cxjxl.do` 一次拿全（每行带所属校区）；若有行缺校区代码，就在同一会话内逐个校区再取。打开页面、切换校区和教学楼都不联网。
- 占用数据按“楼 + 日期”缓存在内存，查询前先查缓存，全部命中就不登录；时间段筛选只在已取到的结果上过滤。目录需要更新且有记住的地点时，目录和占用在同一次会话里一起取。
- 记住上次的校区、教学楼、时间段和最近用过的楼，有记住的地点时打开页面即查询。

[`ComputeFreeSlotsUseCase`](../app/src/main/java/com/example/personal_studio/domain/emptyroom/ComputeFreeSlotsUseCase.kt) 把 `ZYJC` 忙节次转换为 1–13 节的空闲区间，结合用户作息表计算“现在空／空到何时”等状态。结果不是教室实时门禁或实际占座信息。

缓存只存原始占用行，查询时重新计算相对时间；当前键为“楼代码 + 日期”，没有旧设计承诺的到节次边界自动失效，也没有 TTL 或学期／账号隔离。部分缺失响应仍以空列表处理，不能把空教室结果当作所有学校接口已完成严格响应校验的例子。

## 一键同步与后台轮询

[`SyncAllUseCase`](../app/src/main/java/com/example/personal_studio/domain/bitimport/SyncAllUseCase.kt) 按 **课程 → DDL → 考试 → 成绩** 串行执行，各自开会话、登录、同步、关闭；单项失败继续后续项。课程预览在此自动确认。每个失败来源带一个原因（[`SyncFailure`](../app/src/main/java/com/example/personal_studio/domain/bitimport/model/SyncAllModels.kt)：需要验证、密码未被接受、锁定、需评教、网络、空结果、其他），由 `SyncFailures` 从各来源的错误类型映射。“只重试失败项”只重跑失败来源，其余来源沿用上次结果。

同步由单例 [`SyncCenter`](../app/src/main/java/com/example/personal_studio/domain/bitimport/SyncCenter.kt) 在应用级协程里运行，同步面板、今日胶囊和首次同步页共享同一份进度；关闭面板不会中断同步。完成或跳过会设置首次同步标记；至少一项成功时记录 `LoginPrefs.lastSyncAt`。

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
- 新乐学的登录链只在浏览器里实际走通过（含 `/cas//login` 和 broker 回调）；App 内的原生认证链由本地模拟服务器测试覆盖，首次在真机上运行仍需验证。从未登录过新乐学的账号可能遇到 Keycloak 的首次登录页，目前会报“登录后仍未进入”。
- 乐学 token、业务缓存与已下载数据没有完整账号隔离；有效空结果与解析丢失的保护在不同功能之间也不一致。
- 原生短信验证的接口来自学校前端脚本，学校改版就可能失效；失效时退回网页弹窗。“信任这台手机”是否生效、生效多久由学校决定。
- 学校是否需要短信、多久免验证由服务端决定。不能从一次成功登录或单元测试通过推导出长期免验证、全服务可用。

## 排障顺序

先确定失败发生在“探测 → CAS ticket → WebVPN → 服务回调 → 模块预热 → 业务取数 → 解析 → 写入”的哪一步，再修改对应实现。只记录脱敏后的阶段、状态码、host/path、响应类型和 ticket 是否存在，不记录密码、Cookie、完整查询参数、短信或订阅 URL。

对 200 响应先识别是业务数据、登录页还是错误页；对 Retrofit 非 2xx 同时检查 `errorBody()`，不要把 `body()==null` 当成功。用真实且脱敏的结构补测试，再在真实校园网／移动数据下验证；历史验收范围见[决策记录](decisions.md#参考项目与验证边界)。
