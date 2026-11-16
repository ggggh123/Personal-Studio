# 学习工具实现

[返回项目入口](../README.md)

## 对话与模型接口

主链路为 [`ChatDetailViewModel`](../app/src/main/java/com/example/personal_studio/feature/chat/vm/ChatDetailViewModel.kt) → [`SendMessageUseCase`](../app/src/main/java/com/example/personal_studio/domain/chat/SendMessageUseCase.kt) → [`OpenAiCompatibleProvider`](../app/src/main/java/com/example/personal_studio/data/remote/llm/OpenAiCompatibleProvider.kt)。

1. 先保存用户消息，再加载会话历史及仍存在的附件文件，构造模型上下文。
2. 配置按“用户设置 → BuildConfig 默认值”解析，向 `Base URL/chat/completions` 发请求。图片以 JPEG data URL 放入 `image_url` 内容块。
3. SSE 读取 `choices[0].delta.content`，收到 `[DONE]` 后保存完整助手消息。当前不展示服务商的 `reasoning_content`；等待文案是本地轮换提示，不是模型真实思考过程。
4. 点击停止会取消生成任务，并在已有文本后追加“（已停止）”保存；没有文本则不创建空回答。界面中的 token 数是估算值。

新会话默认名为“新对话”（旧数据中的 `session #NNN` 同样视为占位名，显示为“新对话”），第一轮回答保存后由 [`GenerateTitleUseCase`](../app/src/main/java/com/example/personal_studio/domain/chat/GenerateTitleUseCase.kt) 生成标题，判断规则在 [`ChatTitles`](../app/src/main/java/com/example/personal_studio/domain/chat/ChatTitles.kt)；生成标题会多一次模型调用。从未发出消息的会话在离开对话页时删除（`ChatDetailViewModel.onCleared`，旋转或换主题不触发），打开问答列表时也会清理残留的空会话。列表摘要把公式显示为“[公式]”并去掉 Markdown 标记。

流式请求读超时设为无限，连接／写入超时分别为 30／120 秒，避免模型长时间首字等待被固定读超时截断。修改时应同时检查协程取消能否结束网络等待，不能只让界面退出加载态。普通聊天目前会发送完整历史，没有自动摘要或上下文长度预算；长会话仍可能超过模型限制。

### 图片与模型配置

每条消息最多 6 张图片，逐张添加和裁切。旧数据库字段 `attachedImagePath` 通过 [`ChatImagePaths`](../app/src/main/java/com/example/personal_studio/data/repository/ChatImagePaths.kt) 同时兼容旧单路径和新 JSON 数组，未为多图功能升级 schema。改读取端时需保留这个兼容入口。

扫描页可直接作为附件，快捷拍照复用扫描增强链路。会话保存的是本地文件路径，模型调用时才读取并编码图片。界面中的缩略图在后台线程按显示尺寸采样解码并缓存（[`SampledImage`](../app/src/main/java/com/example/personal_studio/ui/components/SampledImage.kt)，缓存键含文件修改时间）；裁切层的显示图也按约 2000 px 采样，裁切坐标按原图宽度换算。

对话页打开期间窗口不随键盘平移或缩放，由页面按键盘高度抬起输入框（[`KeyboardHandledByInsets`](../app/src/main/java/com/example/personal_studio/ui/components/KeyboardInsets.kt)），各厂商系统表现一致。消息列表自下而上布局（`reverseLayout`，索引 0 是底部留白，其后是流式回答和倒序的消息），底边固定：重新打开长对话时停在最新回答，上方回答渲染完公式变高不会把它挤走；流式回答原地向上生长；键盘从下方压缩列表时最新内容仍在视野里。只有发送新消息时，若用户正在翻看历史，才跳回底部。内容不满一屏时仍从顶部排起（`Arrangement.Top`）。

[`CuratedModels`](../app/src/main/java/com/example/personal_studio/core/llm/CuratedModels.kt) 是静态名单；[`SettingsViewModel.selectCuratedModel`](../app/src/main/java/com/example/personal_studio/feature/settings/vm/SettingsViewModel.kt) 不仅改模型代号，还会清除自定义 API Key 和 Base URL，回退到构建默认值。因此自建端点配置后再选内置模型，会改变实际请求的服务。高级配置和连接测试是自定义模型的入口，项目当前没有 Gemini 专用 SDK 实现。

### 公式渲染

完成的消息和知识库正文通过 [`MathMarkdownView`](../app/src/main/java/com/example/personal_studio/ui/components/MathMarkdownView.kt) 使用本地 KaTeX 资源渲染；流式阶段不持续重建公式 WebView。Markdown 由 [`MarkdownHtml`](../app/src/main/java/com/example/personal_studio/ui/components/MarkdownHtml.kt) 转成 HTML：标题、段落（单个换行保留为换行）、有序 / 无序列表（按缩进嵌套，列表可紧跟段落行）、引用、围栏代码、带对齐的管道表格（过宽时横向滚动）、分隔线，以及行内代码、粗体、斜体、删除线、链接（只显示文字，WebView 不跳转）和字面 `<br>`；其余 HTML 一律转义。

渲染中的几个必要约束：先把围栏代码、行内代码和数学片段换成占位符，再处理 Markdown／换行，避免 `<br>` 把公式分隔符拆到不同文本节点，也避免把代码里的 `$` 当成公式；等 `onPageFinished` 后才能调用 `setContent`；字体加载会改变高度，因此用 `ResizeObserver` 回报并缓存高度。当前 viewport 下 CSS px 对应 dp，不能再除以屏幕密度。离开组合时销毁 WebView，防止原生资源累积。

## 扫描与多页文档

相机取景由 CameraX 驱动，实时分析帧产生四角叠层，拍摄仍由用户触发。完整流程为“拍摄 → 自动四角或手动调整 → 透视矫正／滤镜 → 保存页面”。

主要实现入口：[`CameraCaptureViewModel`](../app/src/main/java/com/example/personal_studio/feature/scanner/camera/CameraCaptureViewModel.kt)、[`OnnxEdgeDetector`](../app/src/main/java/com/example/personal_studio/data/scanner/OnnxEdgeDetector.kt)、[`OpenCvEnhancePipeline`](../app/src/main/java/com/example/personal_studio/data/scanner/OpenCvEnhancePipeline.kt)。

- **检测：** ONNX DocAligner 四角热图模型，输入按 256×256、BGR／NCHW 预处理。模型不可用或检测失败时回退 OpenCV，再由界面提供手动框。原生模型加载是惰性的。
- **高分辨率缩小：** 使用 OpenCV `INTER_AREA`。曾经直接双线性缩小导致混叠，拍后检测比低分辨率取景帧更差；这是预处理问题，不能仅靠调置信度阈值解决。
- **矫正：** 四角顺序固定为左上、右上、右下、左下；输出宽高根据四边长度计算，不强制 A4 比例。预览坐标、旋转后坐标和处理图像坐标要保持一致。
- **增强：** 彩色保留矫正图；增强灰度通过局部背景去除和黑白点拉伸突出文字；黑白路径目前使用 3 倍超采样、Otsu 阈值、形态学处理，再用面积插值缩回，以减轻小字锯齿。

[`BitmapIo`](../app/src/main/java/com/example/personal_studio/data/scanner/BitmapIo.kt) 以长边约 2000 像素下采样，实际尺寸受解码采样影响。超采样的中间内存随倍率平方增长；修改滤镜时要在真机检查内存及细字效果，不能沿用旧计划中的固定峰值承诺。

[`CaptureAndEnhancePageUseCase`](../app/src/main/java/com/example/personal_studio/domain/scanner/CaptureAndEnhancePageUseCase.kt) 先写“已透视矫正、未滤镜”的 `orig.jpg`，再生成增强图，并删除临时拍摄文件。因此扫描页的 `originalImagePath` **不是相机未裁切原片**；后续滤镜可重做，不能据此承诺恢复裁切外内容。

### 文档编辑与导出

[`ScanDocumentDetailViewModel`](../app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanDocumentDetailViewModel.kt) 是统一编辑入口，新建与已有文档都在这里追加、重排、删除页面和导出。退出时仅丢弃“本次新建且零页”的空文档；已有文档不自动删除。旧 repository 名称中的 `pending`／`finalize` 不代表当前界面仍有“未完成／已完成”两套流程。

[`ScanRepository`](../app/src/main/java/com/example/personal_studio/data/repository/ScanRepository.kt) 管理数据库与页面文件，封面取排序后的第一页。重排通过 [ScanPageDao](../app/src/main/java/com/example/personal_studio/data/local/db/dao/ScanPageDao.kt) 在事务中先把 ordinal 写为负值，再写最终顺序，避开 `(docId, ordinal)` 唯一约束碰撞。

[`PdfExporter`](../app/src/main/java/com/example/personal_studio/data/scanner/PdfExporter.kt) 按页面顺序导出增强图，每页 PDF 尺寸取对应位图宽高，没有统一纸张排版；输出到缓存目录后用 FileProvider 分享。

## 知识库

核心入口为 [`KnowledgeRepositoryImpl`](../app/src/main/java/com/example/personal_studio/data/repository/KnowledgeRepositoryImpl.kt) 和 [`SourceContextLoader`](../app/src/main/java/com/example/personal_studio/data/repository/SourceContextLoader.kt)。

| 来源 | 提供给模型的内容 | 当前边界 |
| --- | --- | --- |
| 单轮对话 | 选中的助手回答与此前用户问题 | 多图消息只取第一张作为知识条目原图 |
| 整个会话 | 最近约 6000 字符的消息，整理为一条带轮次标记的用户材料 | 按整条消息取舍，可能略超预算；不携带所有历史图片 |
| 扫描页 | 对应增强图片 | 整理题面由模型完成，没有独立本地 OCR |

整段会话作为一份待总结材料发送，而不原样重放成多轮聊天，避免模型继续回答最后一个问题。请求使用结构化 JSON 模式；标题、分类、摘要等必填内容校验失败后，以较低温度重试一次，再失败则给可编辑的原文草稿。网络失败仍作为失败返回。

草稿需经用户预览、修改和确认才能入库。错题判定以 `standardizedQuestion` 是否存在为准；模型可建议至多 3 个相关标题，保存时与库内标题精确匹配形成关系。当前没有向量相似度检索或自动知识图谱。

知识库复制原图到自己的临时目录，保存时移入条目目录，不直接依赖聊天／扫描的源文件。因此删除来源后仍能查看已保存条目；但“重新生成”仍通过源消息或源扫描页重新取上下文，来源删除后会失败。重新生成只更新题面和摘要，保留用户修改过的标题、分类与关系。

### 中文搜索与一致性

[`BigramTokenizer`](../app/src/main/java/com/example/personal_studio/core/bigram/BigramTokenizer.kt) 对中文生成相邻双字 token，拉丁文本按词规范化；索引标题、摘要和标准化题面。底层是 FTS4 `unicode61`，查询先使用带引号 token 的空格连接表达隐式 AND。

[`SearchKbUseCase`](../app/src/main/java/com/example/personal_studio/domain/knowledge/SearchKbUseCase.kt) 在 AND 结果少于 5 条时再查 OR，只有 OR 返回更多条目才采用它。保存、编辑、删除应同时更新主表、FTS 和关联表；repository 已为这些数据库操作使用事务，FTS 删除需要显式执行，不能指望外键级联。

设备 SQLite 的 tokenizer／FTS 语法支持曾与桌面假设不同。保留 Android 端 FTS 回归；不要仅因 JVM 分词测试通过就更换 tokenizer 或改成显式 `AND`。
