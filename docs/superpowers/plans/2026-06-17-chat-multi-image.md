# 聊天单条消息多图(≤6,逐张加+裁剪) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 聊天一条消息可附最多 6 张图(逐张选→裁剪→追加),预览可逐张删,发送时全部拼进 LLM 请求;旧单图消息照常显示。

**Architecture:** 复用现有 `attachedImagePath` 列存「图路径 JSON 数组」(向后兼容旧裸单路径),不改 Room schema/不 bump 版本/不清数据。域/repo/usecase/VM 把单 `String?` 改成 `List<String>`;UI 预览/渲染改多缩略图;LLM Provider 多图本就支持,不动。

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, kotlinx.serialization, JUnit。

## Global Constraints

- 回复用户用简体中文;subagent prompt 用英文。
- 提交信息结尾 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。
- 开发库数据可弃,但本特性**刻意不 bump DB 版本、不做迁移**(复用列)。
- 上限常量 `ChatDetailViewModel.MAX_IMAGES = 6`。
- 设备真机:`./gradlew :app:installDebug`(设备 2407FRK8EC)。

---

### Task 1: 图路径编解码 `ChatImagePaths` + 单测

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/data/repository/ChatImagePaths.kt`
- Test: `app/src/test/java/com/example/personal_studio/data/repository/ChatImagePathsTest.kt`

**Interfaces:**
- Produces: `internal fun encodeChatImagePaths(paths: List<String>): String?`、`internal fun decodeChatImagePaths(raw: String?): List<String>`(包 `com.example.personal_studio.data.repository`)。

- [ ] **Step 1: 写编解码文件**

```kotlin
package com.example.personal_studio.data.repository

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val chatImagePathsJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** 聊天消息的图路径列表 ⇄ `chat_messages.attachedImagePath` 列(复用单列存 JSON 数组)。 */

/** 序列化:空列表→null(列存 null),否则存 JSON 数组字符串。 */
internal fun encodeChatImagePaths(paths: List<String>): String? =
    if (paths.isEmpty()) null else chatImagePathsJson.encodeToString(paths)

/** 反序列化:null/空→[];JSON 数组→列表;解析失败(旧的裸单路径)→单元素列表。 */
internal fun decodeChatImagePaths(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { chatImagePathsJson.decodeFromString<List<String>>(raw) }
        .getOrElse { listOf(raw) }
}
```

- [ ] **Step 2: 写失败测试**

```kotlin
package com.example.personal_studio.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatImagePathsTest {
    @Test fun `encode empty list is null`() {
        assertEquals(null, encodeChatImagePaths(emptyList()))
    }

    @Test fun `encode then decode round trips`() {
        val paths = listOf("/data/a/1.jpg", "/data/b/2.jpg")
        assertEquals(paths, decodeChatImagePaths(encodeChatImagePaths(paths)))
    }

    @Test fun `decode legacy bare single path yields one element`() {
        assertEquals(listOf("/legacy/old.jpg"), decodeChatImagePaths("/legacy/old.jpg"))
    }

    @Test fun `decode null or blank yields empty`() {
        assertEquals(emptyList<String>(), decodeChatImagePaths(null))
        assertEquals(emptyList<String>(), decodeChatImagePaths(""))
        assertEquals(emptyList<String>(), decodeChatImagePaths("   "))
    }
}
```

- [ ] **Step 3: 跑测试**

Run: `./gradlew :app:testDebugUnitTest --tests "*ChatImagePathsTest"`
Expected: PASS(4 测试)。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/data/repository/ChatImagePaths.kt \
        app/src/test/java/com/example/personal_studio/data/repository/ChatImagePathsTest.kt
git commit -m "feat(chat): 图路径列表 ⇄ JSON 列 编解码 ChatImagePaths + 单测"
```

---

### Task 2: 把 `attachedImagePaths: List<String>` 贯穿 域/repo/usecase/loader/VM/Screen + 修测试

> ⚠ 这是一次**原子类型迁移**:`attachedImagePath: String?` → `attachedImagePaths: List<String>` 会同时打断所有引用点,必须一并改完整个 app 才能编译。按下列顺序改,最后一次性 build + 单测 + 装机。

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/data/local/db/entity/ChatMessageEntity.kt:25`(仅注释)
- Modify: `app/src/main/java/com/example/personal_studio/domain/model/ChatModels.kt:18`
- Modify: `app/src/main/java/com/example/personal_studio/data/repository/ChatRepository.kt:31,89,116`
- Modify: `app/src/main/java/com/example/personal_studio/domain/chat/SendMessageUseCase.kt:28,36,54-58,82`
- Modify: `app/src/main/java/com/example/personal_studio/domain/bitgrades/StartGradeChatUseCase.kt:22`
- Modify: `app/src/main/java/com/example/personal_studio/data/repository/SourceContextLoader.kt:61-62`
- Modify: `app/src/main/java/com/example/personal_studio/feature/chat/vm/ChatDetailViewModel.kt:30,86,89-105`
- Modify: `app/src/main/java/com/example/personal_studio/feature/chat/ui/ChatDetailScreen.kt:195-201,281-304,356-362`
- Modify(测试): `app/src/test/.../data/repository/FakeChatRepository.kt`、`ChatRepositoryImplTest.kt`、`ChatSessionSummaryTest.kt`、`app/src/test/.../domain/bitgrades/StartGradeChatUseCaseTest.kt`、`app/src/test/.../domain/chat/GenerateTitleUseCaseTest.kt`

**Interfaces:**
- Consumes: `encodeChatImagePaths`/`decodeChatImagePaths`(Task 1)。
- Produces:
  - `ChatMessage.attachedImagePaths: List<String>`(替换 `attachedImagePath: String?`)。
  - `ChatRepository.appendMessage(sessionId, role, content, attachedImagePaths: List<String> = emptyList(), generationMs, tokenCount, modelUsed): Long`。
  - `SendMessageUseCase.invoke(sessionId, userText, userImagePaths: List<String>, systemPrompt=…): Flow<SendChunk>`。
  - `ChatDetailViewModel.onAttachImage(path: String)`、`onRemoveAttachedImage(path: String)`、`MAX_IMAGES = 6`;`ChatDetailUiState.attachedImagePaths: List<String>`。

- [ ] **Step 1: entity 加注释(列不变)**

`ChatMessageEntity.kt:25`,把
```kotlin
    val attachedImagePath: String? = null,
```
改为
```kotlin
    // 存「图路径 JSON 数组」字符串(单条消息可多图,≤6);向后兼容旧的裸单路径。
    // 编解码见 data/repository/ChatImagePaths.kt。列名/类型不变,故不需 Room 迁移。
    val attachedImagePath: String? = null,
```

- [ ] **Step 2: 域模型改列表**

`ChatModels.kt:18`,把 `val attachedImagePath: String?,` 改为:
```kotlin
    val attachedImagePaths: List<String> = emptyList(),
```

- [ ] **Step 3: repo 签名 + 序列化 + 反序列化**

`ChatRepository.kt`:
1. 接口 `appendMessage` 形参(31 行附近)`attachedImagePath: String?,` → `attachedImagePaths: List<String> = emptyList(),`。
2. impl `appendMessage` 同名形参 → `attachedImagePaths: List<String>,`(impl 无默认值)。
3. impl 内构造 `ChatMessageEntity(... attachedImagePath = attachedImagePath, ...)`(89 行)→ `attachedImagePath = encodeChatImagePaths(attachedImagePaths),`。
4. `ChatMessageEntity.toDomain()`(116 行)`attachedImagePath = attachedImagePath,` → `attachedImagePaths = decodeChatImagePaths(attachedImagePath),`。

(`encodeChatImagePaths`/`decodeChatImagePaths` 同包,无需 import。)

- [ ] **Step 4: SendMessageUseCase 收发列表**

`SendMessageUseCase.kt`:
1. 形参(28 行)`userImagePath: String?,` → `userImagePaths: List<String>,`。
2. 用户消息落库(36 行)`attachedImagePath = userImagePath,` → `attachedImagePaths = userImagePaths,`。
3. 构造历史图字节(54-58 行)整段:
```kotlin
                val images = m.attachedImagePaths.mapNotNull { p ->
                    File(p).takeIf { it.exists() }?.readBytes()
                }
```
4. AI 消息落库(82 行)删掉 `attachedImagePath = null,` 这一行(走 `appendMessage` 的默认 `emptyList()`)。

- [ ] **Step 5: StartGradeChatUseCase 去单图实参**

`StartGradeChatUseCase.kt:22`,删掉 `attachedImagePath = null,` 这一行(走默认 emptyList)。

- [ ] **Step 6: SourceContextLoader 取首图**

`SourceContextLoader.kt:61-62`,把
```kotlin
        val imageBytes = precedingUser?.attachedImagePath?.let { File(it).takeIf(File::exists)?.readBytes() }
        val staged = precedingUser?.attachedImagePath?.let { imageStore.stageCopy(it) }
```
改为
```kotlin
        val firstImagePath = precedingUser?.let { decodeChatImagePaths(it.attachedImagePath).firstOrNull() }
        val imageBytes = firstImagePath?.let { File(it).takeIf(File::exists)?.readBytes() }
        val staged = firstImagePath?.let { imageStore.stageCopy(it) }
```
(`decodeChatImagePaths` 同包 data.repository,无需 import。)

- [ ] **Step 7: ChatDetailViewModel 列表态 + 加/删 + 上限 + onSend**

`ChatDetailViewModel.kt`:
1. `ChatDetailUiState` 字段(30 行)`val attachedImagePath: String? = null,` → `val attachedImagePaths: List<String> = emptyList(),`。
2. 替换 `onAttachImage`(86 行)为加/删两个方法:
```kotlin
    fun onAttachImage(path: String) = _uiState.update {
        if (it.attachedImagePaths.size >= MAX_IMAGES) it
        else it.copy(attachedImagePaths = it.attachedImagePaths + path)
    }
    fun onRemoveAttachedImage(path: String) = _uiState.update {
        it.copy(attachedImagePaths = it.attachedImagePaths - path)
    }
```
3. `onSend`(89-101 行)头部:
```kotlin
    fun onSend() {
        val text = _uiState.value.input.trim()
        val imagePaths = _uiState.value.attachedImagePaths
        if (text.isBlank() && imagePaths.isEmpty()) return

        _uiState.update {
            it.copy(
                input = "",
                attachedImagePaths = emptyList(),
                isSending = true,
                streamingText = "",
            )
        }
```
4. send 调用(105 行)`send(sessionId = sessionId, userText = text, userImagePath = imagePath)` → `send(sessionId = sessionId, userText = text, userImagePaths = imagePaths)`。
5. 在 `@AssistedFactory interface Factory { … }` 之后(类体内)加:
```kotlin
    companion object { const val MAX_IMAGES = 6 }
```

- [ ] **Step 8: ChatDetailScreen 渲染多图 + 预览多缩略图 + [+] 限 6**

`ChatDetailScreen.kt`:
1. 用户消息渲染(195-201 行)整段:
```kotlin
                    MessageRole.USER -> {
                        val paths = m.attachedImagePaths
                        UserPromptLine(
                            text = m.contentMarkdown,
                            imageThumb = if (paths.isNotEmpty()) {
                                {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        paths.forEach { ChatImageThumbnail(path = it) }
                                    }
                                }
                            } else null,
                        )
                    }
```
2. 附件预览行(281-304 行)整段替换为横排可滚缩略图(每张可删):
```kotlin
        // Attached images preview row (≤ MAX_IMAGES, each removable)
        if (state.attachedImagePaths.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.attachedImagePaths.forEach { p ->
                    Box {
                        ChatImageThumbnailSmall(path = p)
                        Text(
                            "[x]",
                            style = MaterialTheme.typography.labelSmall,
                            color = Carmine,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clickableNoRipple { vm.onRemoveAttachedImage(p) },
                        )
                    }
                }
            }
        }
```
3. `[+]` 入口(356-362 行)改为满 6 置灰不可点:
```kotlin
            Spacer(Modifier.width(8.dp))
            val canAttach = state.attachedImagePaths.size < ChatDetailViewModel.MAX_IMAGES
            Text(
                "[+]",
                color = if (canAttach) Cyan else FoamDim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickableNoRipple { if (canAttach) showAttachmentSheet = true },
            )
```
4. 裁剪 `onConfirm`(420-423 行)保持 `vm.onAttachImage(croppedPath)`(VM 内已限 6),无需改。
5. 补缺失 import(若未存在):`androidx.compose.foundation.horizontalScroll`、`androidx.compose.foundation.rememberScrollState`、`androidx.compose.foundation.layout.Box`。(`Row`/`Arrangement`/`Alignment`/`ChatDetailViewModel` 本文件已用,通常已 import;编译报错再补。)

- [ ] **Step 9: 修被打断的测试**

编译会在这些文件报「`attachedImagePath` 不存在 / `appendMessage` 签名不符 / `userImagePath` 不存在」。逐个改(规则:旧单值 `x` → `listOfNotNull(x)`,旧 `null` → `emptyList()`):
- `FakeChatRepository.kt`:`appendMessage` 形参 `attachedImagePath: String?` → `attachedImagePaths: List<String>`;若内部存了该值,改存列表;构造 `ChatMessage(...)` 的 `attachedImagePath = …` → `attachedImagePaths = …`。
- `ChatRepositoryImplTest.kt`:`appendMessage(attachedImagePath = "x")` → `appendMessage(attachedImagePaths = listOf("x"))` 或 `= emptyList()`;断言 `msg.attachedImagePath` → `msg.attachedImagePaths`(单值断言改 `listOf("x")`/`emptyList()`)。
- `ChatSessionSummaryTest.kt`、`GenerateTitleUseCaseTest.kt`:构造 `ChatMessage(attachedImagePath = null/…)` → `attachedImagePaths = emptyList()/listOf(…)`。
- `StartGradeChatUseCaseTest.kt`:若断言 `appendMessage` 被以 `attachedImagePath = null` 调用,改为 `attachedImagePaths = emptyList()`(或去掉该实参匹配)。

先读每个文件确认实际用法,再改。

- [ ] **Step 10: 全量编译 + 单测**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL;含新 `ChatImagePathsTest` 与全部既有聊天测试通过。失败则按报错回到对应 Step 修。

- [ ] **Step 11: 装机**

Run: `./gradlew :app:installDebug --console=plain`
Expected: Installed on 1 device。

- [ ] **Step 12: 提交**

```bash
git add app/src/main app/src/test
git commit -m "feat(chat): 单条消息支持多图(<=6,逐张加+裁剪),attachedImagePaths 贯穿域/repo/VM/UI + 修测试"
```

---

## 真机 DoD(装机后人工验)

1. 进任一会话,点 `[+]` → 选图 → 裁剪 → 缩略图进预览;重复加到 6 张;第 6 张后 `[+]` 置灰不可点。
2. 预览行每张缩略图右上角 `[x]` 可单独删,删后 `[+]` 恢复可点。
3. 连同文字发送 → 消息气泡里多张缩略图横排显示;AI 能基于全部图作答。
4. 仅图无文字也可发。
5. 旧的单图历史消息仍正常显示(向后兼容)。
6. 重进会话/重启 App,已发消息的多图仍在(JSON 列持久化)。

## Self-Review 记录

- Spec 覆盖:存储(Task1+Step1/3)、域/repo/usecase/loader(Step2-6)、VM(Step7)、Screen(Step8)、测试(Task1 Step2 + Step9)、DoD —— 全覆盖。
- 类型一致:`attachedImagePaths: List<String>`、`userImagePaths: List<String>`、`onAttachImage(String)`/`onRemoveAttachedImage(String)`/`MAX_IMAGES` 在各 Step 用法一致。
- 无占位:Step9 测试修给出明确规则 + 文件清单(执行时读各文件按规则改;因 5 个测试文件内容未逐一展开,执行者须先读后改)。
