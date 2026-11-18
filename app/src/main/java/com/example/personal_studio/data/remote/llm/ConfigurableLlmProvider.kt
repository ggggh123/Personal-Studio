package com.example.personal_studio.data.remote.llm

import com.example.personal_studio.core.llm.BundledLlm
import com.example.personal_studio.core.llm.GenerationSettings
import com.example.personal_studio.core.llm.LlmEndpoint
import com.example.personal_studio.core.llm.LlmEndpoints
import com.example.personal_studio.core.llm.activeEndpoint
import com.example.personal_studio.data.local.datastore.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * The app's LLM client. Each request reads the current settings: the endpoint (built-in DeepSeek
 * or the person's own, in the OpenAI, Anthropic or Gemini format) and the generation settings
 * (reasoning level, temperature, output length, top-p). Streaming is SSE in all three formats;
 * only answer text is surfaced, never the model's thinking.
 */
class ConfigurableLlmProvider(
    private val prefs: UserPreferencesRepository,
    private val bundled: BundledLlm,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : LLMProvider {

    override val name: String = "configurable"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private suspend fun current(): Pair<LlmEndpoint, GenerationSettings> =
        prefs.activeEndpoint(bundled).first() to prefs.generation.first()

    override fun generate(messages: List<LlmMessage>, temperature: Float): Flow<LlmChunk> = flow {
        val (endpoint, settings) = current()
        problem(endpoint)?.let { emit(LlmChunk.Error(it, retryable = false)); return@flow }
        val model = LlmEndpoints.modelFor(endpoint, messages.any { it.images.isNotEmpty() })
        val format = LlmWireFormat.of(endpoint.format)
        val body = format.body(model, messages, temperature, settings, stream = true, jsonOutput = false)
        stream(request(format, endpoint, model, body, stream = true), format, model)
    }
        .flowOn(Dispatchers.IO)
        .catch { t -> emit(LlmChunk.Error(t.message ?: "未知错误", retryable = true)) }

    override suspend fun generateStructured(
        messages: List<LlmMessage>,
        jsonSchema: String,
        temperature: Float,
    ): String = withContext(Dispatchers.IO) {
        val (endpoint, settings) = current()
        problem(endpoint)?.let { error(it) }
        val model = LlmEndpoints.modelFor(endpoint, messages.any { it.images.isNotEmpty() })
        val format = LlmWireFormat.of(endpoint.format)
        val instruction = LlmMessage(
            role = LlmRole.SYSTEM,
            text = """
                You must respond with valid JSON conforming to this schema:
                $jsonSchema

                Return only the JSON, no Markdown fences, no prose.
            """.trimIndent(),
        )
        val body = format.body(model, mergeSchemaInstruction(messages, instruction), temperature, settings, stream = false, jsonOutput = true)
        httpClient.newCall(request(format, endpoint, model, body, stream = false)).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            if (!response.isSuccessful) error("HTTP ${response.code}: ${LlmWireFormat.errorMessage(root) ?: text.take(300)}")
            val content = root?.let(format::text)?.takeIf { it.isNotBlank() } ?: error("upstream returned no content")
            stripFences(content)
        }
    }

    /** Why a request cannot be sent at all, in words for the person; null when it can. */
    private fun problem(endpoint: LlmEndpoint): String? = when {
        endpoint.apiKey.isBlank() -> "还没有可用的 API 密钥：请到 设置 → AI 模型 选择内置模型，或在自定义接口里填写密钥。"
        endpoint.model.isBlank() -> "自定义接口还没有填写模型名称：请到 设置 → AI 模型 → 自定义接口 填写。"
        else -> null
    }

    /**
     * Keeps a single SYSTEM turn: the schema instruction joins the caller's first SYSTEM message,
     * or comes first on its own. Some backends drop or downgrade a second SYSTEM turn.
     */
    private fun mergeSchemaInstruction(messages: List<LlmMessage>, instruction: LlmMessage): List<LlmMessage> =
        if (messages.firstOrNull()?.role == LlmRole.SYSTEM) {
            val first = messages.first()
            listOf(first.copy(text = first.text + "\n\n" + instruction.text)) + messages.drop(1)
        } else {
            listOf(instruction) + messages
        }

    private fun request(format: LlmWireFormat, endpoint: LlmEndpoint, model: String, body: JsonObject, stream: Boolean): Request =
        Request.Builder()
            .url(format.url(endpoint, model, stream))
            .apply { format.headers(endpoint, stream).forEach { (k, v) -> header(k, v) } }
            .header("Content-Type", "application/json")
            .post(json.encodeToString(JsonElement.serializer(), body).toRequestBody(JSON_MEDIA_TYPE))
            .build()

    private suspend fun FlowCollector<LlmChunk>.stream(request: Request, format: LlmWireFormat, model: String) {
        val call = httpClient.newCall(request)
        // Cancelling the coroutine (the person taps ⏹) cancels the call and closes the socket, so a
        // read blocked while the model is thinking returns at once.
        coroutineContext.job.invokeOnCompletion { call.cancel() }
        call.execute().use { response ->
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                emit(LlmChunk.Error("HTTP ${response.code}: ${LlmWireFormat.errorMessage(root) ?: text.take(500)}", retryable = response.code >= 500))
                return@use
            }
            val source = response.body?.source() ?: run {
                emit(LlmChunk.Error("Empty response body", retryable = true))
                return@use
            }
            var gotAnyContent = false
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue // Anthropic's `event:` lines, comments, blanks
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                if (payload == "[DONE]") break
                val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: continue
                when (val event = format.event(root)) {
                    is StreamEvent.Text -> { gotAnyContent = true; emit(LlmChunk.Text(event.delta)) }
                    is StreamEvent.Failed -> { emit(LlmChunk.Error(event.message, retryable = false)); return@use }
                    StreamEvent.End -> break
                    StreamEvent.Skip -> Unit
                }
            }
            if (!gotAnyContent) {
                emit(LlmChunk.Error("no content received from upstream (model may be unavailable)", retryable = true))
                return@use
            }
            emit(LlmChunk.Done(totalTokens = null, model = model))
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Models asked for JSON sometimes still wrap it in a Markdown fence. */
        internal fun stripFences(text: String): String {
            val t = text.trim()
            if (!t.startsWith("```")) return t
            return t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").removeSuffix("```").trim()
        }

        private fun defaultHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                // 0 = no read timeout: a reasoning model may think for minutes (⏹ stops it).
                .readTimeout(0, TimeUnit.SECONDS)
                // Vision requests on mobile uplinks send multi-MB images; 30 s was not enough.
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
    }
}
