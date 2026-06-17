# 聊天单条消息多图(≤6,逐张加+裁剪)设计

日期：2026-06-17
状态：已获批,待写实现计划

## 背景与目标

当前一条聊天消息只能附 1 张图(`attachedImagePath: String?` 贯穿 entity/域/repo/usecase/UI)。底层 LLM 层早支持多图(`LlmMessage(images: List<ByteArray>)` + OpenAI array-of-parts)。目标：单条消息最多 **6 张**图,**逐张添加并保留裁剪**(沿用现有 pick→crop→追加),预览可逐张删,发送时全部拼进请求。

## 决策(已与用户确认)

- 上限 **6 张**;**逐张添加 + 保留裁剪**(不做相册多选)。
- **存储:复用现有 `attachedImagePath` 列存 JSON 数组,不改 Room schema、不 bump 版本、不清数据**;反序列化向后兼容旧裸单路径。

## ① 存储(无 schema 变更 / 无数据清空)

- `ChatMessageEntity.attachedImagePath: String?` **列保留不动**,语义改为"一个图路径的 JSON 数组(向后兼容旧裸单路径)";加注释。
- 新建 `data/repository/ChatImagePaths.kt`(internal,可单测):
  - `encodeChatImagePaths(paths: List<String>): String?` —— 空→null,否则 `Json.encodeToString(paths)`。
  - `decodeChatImagePaths(raw: String?): List<String>` —— null/空→`[]`;`runCatching { decode<List<String>>(raw) }.getOrElse { listOf(raw) }`(JSON 数组解析,失败当旧裸单路径)。

## ② 域 / Repo / UseCase 贯穿列表

- `ChatMessage.attachedImagePath: String?` → `attachedImagePaths: List<String> = emptyList()`。
- `ChatRepository.appendMessage(..., attachedImagePaths: List<String> = emptyList(), ...)`(替换 `attachedImagePath: String?`);impl 落库 `attachedImagePath = encodeChatImagePaths(attachedImagePaths)`;`toDomain` 读 `attachedImagePaths = decodeChatImagePaths(attachedImagePath)`。
- `SendMessageUseCase`:形参 `userImagePath: String?` → `userImagePaths: List<String>`;用户消息 `attachedImagePaths = userImagePaths`、AI 消息走默认 `emptyList()`;构造 `images = m.attachedImagePaths.mapNotNull { File(it).takeIf { f -> f.exists() }?.readBytes() }`。
- `StartGradeChatUseCase`:去掉 `attachedImagePath = null`(走默认)。
- `SourceContextLoader`(kb 归档):`decodeChatImagePaths(precedingUser.attachedImagePath).firstOrNull()` 作 kb 代表图(取首图)。

## ③ ChatDetailViewModel

- 状态 `attachedImagePath: String?` → `attachedImagePaths: List<String> = emptyList()`。
- `onAttachImage(path: String)` → **追加**(`size >= MAX_IMAGES` 则忽略);新增 `onRemoveAttachedImage(path: String)` → 移除。
- `onSend`:取 `attachedImagePaths`;`text.isBlank() && imagePaths.isEmpty()` 才早返回;清空 `attachedImagePaths = emptyList()`;`send(…, userImagePaths = imagePaths)`。
- `companion object { const val MAX_IMAGES = 6 }`。

## ④ ChatDetailScreen

- 附件预览行(原单图+[x])→ **横排可滚缩略图**,每张右上角 `[x]` 单独删 → `vm.onRemoveAttachedImage(path)`;`state.attachedImagePaths.isNotEmpty()` 时显示。
- `[+]` 入口:`attachedImagePaths.size < 6` 才可点(Cyan),满 6 置灰(FoamDim)不可点。
- 裁剪 `onConfirm` 仍 `vm.onAttachImage(cropped)`(VM 内追加+限 6);加图流程(AttachmentSheet/相机/扫描→crop)不变。
- 消息渲染:用户消息 `imageThumb` 槽填一排缩略图 `m.attachedImagePaths.forEach { ChatImageThumbnail(it) }`(`UserPromptLine` 不改,单槽内放 Row);空列表→无槽。

## 测试

- 单测 `ChatImagePathsTest`:encode 空→null、encode/decode 往返、decode 旧裸单路径→单元素、null/""→[]。
- 同步更新引用 `attachedImagePath`/`appendMessage` 旧签名的现有测试(`FakeChatRepository`/`ChatRepositoryImplTest`/`ChatSessionSummaryTest`/`StartGradeChatUseCaseTest`/`GenerateTitleUseCaseTest`):字段 `attachedImagePath=x`→`attachedImagePaths=listOfNotNull(x)`/`emptyList()`、`FakeChatRepository.appendMessage` 签名改 List。编译/全量单测守。
- 真机 DoD:逐张加图(裁剪)到 6、预览单删、满 6 禁加、发送后模型看到全部图、旧单图消息照常显示。

## 不做 / 保留

- 不做相册多选、不做发送后编辑图、AI 消息不带图。
- **无 DB 版本 bump / 无数据清空 / 无网络改动**(Provider 多图早支持)。

## 影响面

改:`ChatMessageEntity`(注释)、`ChatModels`、`ChatRepository`(签名+序列化)、`SendMessageUseCase`、`StartGradeChatUseCase`、`SourceContextLoader`、`ChatDetailViewModel`、`ChatDetailScreen`;新建 `data/repository/ChatImagePaths.kt` + `ChatImagePathsTest`;更新 5 个现有测试。
