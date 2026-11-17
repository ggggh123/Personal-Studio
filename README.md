# Personal Studio

面向个人学习生活的 Android 应用：把 AI 问答、文档扫描、知识库、日程与北京理工大学教务数据放在一起。本地内容由 Room 和应用私有文件保存；模型请求和学校同步需要联网。

本文及 `docs/` 于 **2026-11-15 按当前工作区源码核对**，包含此前尚未提交的教务修复。历史实测日期单独保留，不表示这些功能已在今天重新完成线上验收。

## 当前功能

| 功能 | 已实现的主要能力 |
| --- | --- |
| AI 对话 | OpenAI 兼容接口、流式回答、停止生成、每条最多 6 张图片、拍照或扫描页提问、Markdown 与公式显示 |
| 扫描 | 实时四角检测、手动校正、透视矫正、彩色／增强灰度／黑白滤镜、多页编辑、PDF 导出与分享 |
| 知识库 | 从单轮对话、整个会话或扫描页生成可编辑草稿；分类、错题原图、关联条目、中文全文搜索 |
| 日程 | 日时间线、周课表、手动课程与待办、课程系列管理、作息表设置、本地提醒 |
| 学校服务 | 课表导入、成绩与详情、GPA／预测分析、乐学与新乐学作业 DDL、考试安排、空教室查询、一键同步 |
| 自动同步 | 可选的成绩与作业后台轮询；发现新增内容后通知 |

底栏包含今日（日程，启动后的首页）、问答、资料（知识库与扫描文档）、校园四个入口，中间的 ＋ 用于扫描文档、拍题提问、新建待办和添加课程；教务功能集中在校园，设置从右上角头像进入。未登录学校账户也可使用本地功能；AI 功能另需配置可用的模型服务。

目前没有独立 OCR、向量检索／知识图谱、云端多设备同步或通用日历文件导入。知识库中的题面整理来自模型，乐学 iCal 是专用同步接口。

## 构建与运行

使用 Android Studio 打开仓库根目录，安装 Android SDK 35。当前配置为 `minSdk 30`、`targetSdk 35`，APK 仅包含 `arm64-v8a` 原生库，测试设备或模拟器须匹配这一架构。

Gradle Wrapper 为 8.11.1，daemon 配置要求 JDK 21，Java／Kotlin 编译目标为 17；这两个版本的用途不同。工具链和依赖版本分别见 [daemon 配置](gradle/gradle-daemon-jvm.properties)、[版本目录](gradle/libs.versions.toml)及 [app 构建配置](app/build.gradle.kts)。首次构建需要下载依赖。

在仓库根目录运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。连接开启 USB 调试的设备后，可执行：

```powershell
.\gradlew.bat :app:installDebug
```

Debug 版可调试、不预编译，每个页面第一次打开都会明显更慢，不能用来判断动画是否流畅。日常使用或检查流畅度时安装 `profile` 版：它就是 release 代码，不可调试，用 debug 签名，所以能直接覆盖 debug 版并保留数据（反过来也可以）：

```powershell
.\gradlew.bat :app:installProfile
```

AI 的 API Key、Base URL、模型名可在应用设置中配置；Base URL 填服务基础地址，客户端会追加 `/chat/completions`。编译不要求提供 API Key。如需给自己的构建设置默认值，可在已被 Git 忽略的 `local.properties` 中填写：

```properties
API_KEY=YOUR_API_KEY
API_BASE_URL=https://YOUR_PROVIDER/v1
DEFAULT_LLM_MODEL=YOUR_MODEL_ID
```

这些默认值会进入 APK 的 `BuildConfig`，不能作为保密分发机制。内置模型名单是静态配置，不保证列出的模型仍由服务商提供；选择内置模型会清除自定义端点和密钥设置，详见[学习工具实现](docs/learning-tools.md)。

## 开发文档

| 文档 | 阅读用途 |
| --- | --- |
| [架构与维护边界](docs/architecture.md) | 代码入口、存储、主题、验证方式及仍存在的限制 |
| [学习工具实现](docs/learning-tools.md) | 对话、扫描、知识库的主要链路与约束 |
| [日程与提醒](docs/timeline.md) | 课程展开、学期锚点、导入覆盖规则与提醒调度 |
| [学校系统接入](docs/school-integration.md) | CAS／WebVPN、Cookie、业务数据源、轮询及排障 |
| [重要决策与经验](docs/decisions.md) | 方案变更原因、曾经踩过的坑、历史验证边界 |
| [界面改版：天光](docs/ui-redesign.md) | 新设计系统、迁移策略与分阶段实施计划（进行中） |

维护时直接更新对应专题，保留影响后续实现的原因和证据即可。已执行的施工计划、任务清单、整段源码副本及未采用的视觉原型不再作为常驻文档；已提交版本可从 Git 历史追溯。
