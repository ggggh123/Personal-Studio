# 取消回复限时 + 手动叫停 设计

日期：2026-06-17
状态：已获批,待实现

## 背景与目标

推理模型思考几分钟常见,而当前 LLM 客户端 `readTimeout=120s`(流式下=首字节/字节间隔上限),长思考会被掐断报错。目标:① **取消对模型回复的限时**;② 加**用户手动「⏹ 停止」按钮**叫停正在生成的回复,并**保留已生成的半截内容**(标「（已停止）」)。

## 决策(已确认)

- 取消回复限时:`readTimeout=0`(无限)。`connectTimeout`(30s)、`writeTimeout`(120s) 保留。
- 叫停保留半截回复(存为 AI 消息 + 「（已停止）」)。

## ① 取消限时 `OpenAiCompatibleProvider.defaultHttpClient()`

`.readTimeout(120, SECONDS)` → `.readTimeout(0, SECONDS)`(OkHttp 0=不超时)。connect/write 不变。配套叫停按钮兜住"无限等待"(用户可随时停)。

## ② 叫停能真正中断阻塞读 `OpenAiCompatibleProvider.streamCompletion`

思考阶段无字节在传 + readTimeout=0,单纯取消协程不能立刻中断阻塞的 `readUtf8Line`;需把 OkHttp `Call.cancel()` 挂到协程取消上(关 socket → 读抛异常 → 流结束):
```
val call = httpClient.newCall(request)
coroutineContext.job.invokeOnCompletion { call.cancel() }   // 正常完成时 call 已结束,cancel 为 no-op,安全
call.execute().use { response -> ... }
```
(`import kotlin.coroutines.coroutineContext` + `import kotlinx.coroutines.job`。)

## ③ ChatDetailViewModel:sendJob + onStop

- `private var sendJob: Job? = null`;`onSend` 头部 `if (_uiState.value.isSending) return`;把发送协程赋给 `sendJob = viewModelScope.launch { … }`。
- `fun onStop()`:取消 `sendJob`;读当前 `streamingText`;`streamingText=null, isSending=false`;若半截非空 → `repo.appendMessage(sessionId, MessageRole.AI, partial + "\n\n（已停止）", modelUsed = activeModel)`(其余参数默认)。持久化后经 messages flow 正常渲染成 AI 气泡。
- import `kotlinx.coroutines.Job`、`com.example.personal_studio.domain.model.MessageRole`。

> 取消时 `SendMessageUseCase` 的 `LlmChunk.Done`(落库 AI 消息)分支不会执行,故只有 onStop 落 1 条半截消息,无重复。

## ④ ChatDetailScreen:停止按钮

发送区:`state.isSending` 时显示「⏹ 停止」(Carmine)→ `vm.onStop()`;否则「↵ 发送」(Amber)→ `vm.onSend()`。

## 测试

- 该改动是网络超时 + socket 取消 + 协程取消的集成行为,单测易 flaky 且低值;**靠真机 DoD**,并保证既有单测全绿(无回归)。
- 真机 DoD:① 选推理模型发问,思考 >2 分钟不再报超时、思考提示一直转;② 生成中点「⏹ 停止」→ 立刻停(思考阶段也能停)、已出的文字留在气泡末尾带「（已停止）」;③ 停后可正常再发;④ 普通快速回复不受影响。

## 不做 / 保留

- 不改 connect/write 超时;不做"继续生成";不重试。
- 接 `feat/chat-multi-image` 分支(聊天 UI 改进批次)。
