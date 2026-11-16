# 架构与维护边界

[返回项目入口](../README.md)

## 代码组织

项目是单一 `:app` 模块，使用 Kotlin、Jetpack Compose、Hilt、Room、DataStore、Coroutines／Flow。没有应用自有的远端业务后端。

源码根目录为 [`app/src/main/java/com/example/personal_studio`](../app/src/main/java/com/example/personal_studio)。主要职责如下：

| 目录 | 职责 |
| --- | --- |
| `feature/` | 各功能 Screen、ViewModel、界面状态和交互编排 |
| `domain/` | 登录、同步、课程映射、成绩计算、知识整理等用例与模型 |
| `data/local/` | Room Entity／DAO、DataStore 和学校加密凭据 |
| `data/repository/` | 对话、扫描、知识库和日程的持久化访问 |
| `data/remote/llm/` | 模型接口与 OpenAI 兼容实现 |
| `data/network/bit/` | 学校服务、认证跳转、Cookie 和网络方式选择 |
| `data/scanner/` | ONNX／OpenCV 处理和 PDF 导出 |
| `core/`、`ui/` | 依赖注入、工具函数、通知／Worker、导航和共享界面 |

这是一套代码组织方式，没有强制的模块依赖隔离；部分用例仍直接接触 DAO 或 Android 图像类型。新增功能优先沿用现有入口，不能把早期“严格纯领域层”的设想当作现状。

[`PersonalStudioApp`](../app/src/main/java/com/example/personal_studio/PersonalStudioApp.kt) 初始化 OpenCV、崩溃记录及后台轮询，并提供 `HiltWorkerFactory`。[`MainActivity`](../app/src/main/java/com/example/personal_studio/MainActivity.kt) 启动 Compose 界面并请求重排近期提醒。界面主要通过 Flow 观察本地数据，同步用例负责网络取数和写入。

## 数据与配置

| 内容 | 当前存储方式 | 维护时需要注意 |
| --- | --- | --- |
| 对话、扫描索引、知识库、日程、成绩 | Room；[AppDatabase](../app/src/main/java/com/example/personal_studio/data/local/db/AppDatabase.kt) 当前为 v13 | 图片不在数据库中；数据库与文件系统不是一个事务 |
| 扫描、对话图片、知识库图片 | 应用私有文件；扫描目录为 `filesDir/scans`，知识库为 `filesDir/kb` | 删除或复制图片应通过对应 repository／image store |
| 模型配置、学期／作息表、通知及轮询状态 | Preferences DataStore | 模型 API Key 也在这里，不能与学校凭据的加密存储混为一谈 |
| 学校学号、密码、上次访问方式 | [ImportCredentialPrefs](../app/src/main/java/com/example/personal_studio/data/local/datastore/ImportCredentialPrefs.kt)，Android Keystore 支持的 EncryptedSharedPreferences | 保存凭据不代表每个学校服务当前仍有有效会话 |
| 学校浏览器会话 | [BitBrowserSessions](../app/src/main/java/com/example/personal_studio/data/network/bit/BitBrowserSessions.kt) 管理 CookieManager | 同账号保留，切账号／退出时清除；WebView 数据排除在备份外 |
| PDF 与崩溃记录 | PDF 在 `cacheDir/export`；崩溃堆栈在 `filesDir/crash-logs` | PDF 通过 FileProvider 分享；共享诊断前检查敏感内容 |

Room schema 快照在 [`app/schemas`](../app/schemas)，表关系以 Entity／DAO 为准，不在文档复制完整字段清单。当前[数据库配置](../app/src/main/java/com/example/personal_studio/core/di/DatabaseModule.kt)仍使用 `fallbackToDestructiveMigration()`，没有显式升级迁移。**增加 schema 版本而缺少迁移路径会重建数据库**；不能据早期“开发库可丢弃”的决定承诺现有用户的数据安全。

学校退出登录会清凭据、乐学日历订阅地址、Cookie 并关闭两个轮询开关。退出时可选择同时删除本机的学校数据（[ClearSchoolDataUseCase](../app/src/main/java/com/example/personal_studio/domain/auth/ClearSchoolDataUseCase.kt)：成绩与排名、AI 学情报告缓存、导入的课表、乐学作业、考试及其提醒，以及同步记录），手动添加的课程、待办和日程保留。不勾选时已下载的内容仍在本机；业务缓存没有完整的账号隔离。具体实现见 [LogoutUseCase](../app/src/main/java/com/example/personal_studio/domain/auth/LogoutUseCase.kt)。

## 界面约定

界面使用“天光”主题：配色随北京的真实太阳高度变化，白天为纸本浅色、夜里为灯下深色，深浅只在拂晓和日落后、且在回到前台或切换页面时切换。令牌、动效与各页设计见 [界面改版](ui-redesign.md)；实现入口为 [`ui/theme`](../app/src/main/java/com/example/personal_studio/ui/theme)，所有界面通过 `Tg.colors` 取色、`TgMotion` 取动效，不写固定色值。

公共组件在 [`ui/components`](../app/src/main/java/com/example/personal_studio/ui/components)：子页面标题栏 `TgTopBar`，对话框 `TgDialog` / `TgConfirmDialog` / `TgInputDialog`，底部面板 `TgBottomSheet` 与 `SheetAction`，按钮 `TgPillButton`，开关 `TgSwitch`，分段 `TgSegmented`，以及 [`TgKit`](../app/src/main/java/com/example/personal_studio/ui/components/TgKit.kt) 里的卡片、筛选胶囊、输入框、设置行、空态和处理中遮罩，[`TgPickers`](../app/src/main/java/com/example/personal_studio/ui/components/TgPickers.kt) 里的日期与时钟对话框。有输入框的整页调用 `KeyboardHandledByInsets` 并自己 `imePadding`。相机、裁边、增强预览和图片裁切始终使用深色（`DarkCanvas`）；导出为图片的成绩分享卡固定使用正午纸面配色（`PaperCanvas`）；外部学校页面保留网页自身样式。交互文案以中文为主；路由、模型代号等技术值保留原样。

## 验证入口

日常构建命令见 [README](../README.md#构建与运行)。按改动选择验证范围：

| 改动 | 有意义的验证 |
| --- | --- |
| 纯计算、解析、ViewModel、网络编排 | [`app/src/test`](../app/src/test) 中相关 JVM 测试；网络测试使用 MockWebServer |
| Room 查询／FTS、真实 SQLite 行为 | [`app/src/androidTest`](../app/src/androidTest)；连接设备后运行 `:app:connectedDebugAndroidTest` |
| 扫描／相机、WebView、权限、提醒 | Android 设备操作；JVM 测试不能代替图像质量、布局和厂商后台行为验证 |
| 学校协议变化 | 脱敏响应样本回归，加校园网和移动数据下的实际业务查询 |
| 仅文档 | 检查本地链接、源码引用、失效文档引用和 `git diff --check` |

[`app/src/test/resources/bit-fixtures`](../app/src/test/resources/bit-fixtures) 是协议回归输入，不是线上兼容性的证明。测试样本应去掉账号、票据、Cookie、手机号和日历订阅 token。学校日志只保留阶段、方法、host/path、状态等必要信息；不要恢复旧调试日志中的整包 BODY 输出。

## 尚未解决的维护边界

- **同步原子性：** 成绩和导入课程的删除／写入，以及 DDL／考试的多行更新，目前没有统一包在数据库事务内。获取失败时尽量保留旧数据，并不等于写入中途失败也能回滚。
- **提醒覆盖：** 自动重排扫描未来 7 天，在启动、开机和每次课表／DDL／考试写入后运行；超过 7 天不打开应用且不重启时仍会漏提醒，见[日程说明](timeline.md#提醒调度)。
- **账号切换：** Cookie 已按账号管理；成绩库、日程及持久化的乐学订阅 URL 尚未形成完整的账号隔离机制。
- **外部服务恢复：** 尚无按学校服务复用／刷新的统一 `ensureSession`；部分 CAS 临时错误仍会被归为普通失败，详见[学校接入边界](school-integration.md#当前边界)。
- **发布信息：** 构建中仍为 `versionCode=1`、`versionName=0.1.0-p0`。这个标签落后于实际功能，不能用它判断只完成了早期阶段；重新发布前需要整理版本与数据迁移。

这些是当前代码的限制记录，本次文档整理没有顺带修改这些行为。
