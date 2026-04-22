package com.example.personal_studio.data.remote.llm

import android.util.Base64
import com.example.personal_studio.data.local.datastore.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Generic OpenAI-compatible LLM provider.
 *
 * Works with any server that speaks the OpenAI chat-completions API format:
 *  - OpenAI (`https://api.openai.com/v1`)
 *  - OpenRouter (`https://openrouter.ai/api/v1`)
 *  - Ollama (`http://<host>:11434/v1`)
 *  - LM Studio (`http://<host>:1234/v1`)
 *  - DeepSeek, Together, Fireworks, Groq, etc.
 *
 * This class appends `/chat/completions` to the configured base URL.
 *
 * Streaming is SSE: `data: {json}` lines terminated by `data: [DONE]`.
 * Multimodal uses OpenAI's array-of-parts content format.
 *
 * The OpenRouter-flavored `HTTP-Referer` and `X-Title` headers are sent unconditionally
 * — they're harmless for other servers (ignored) and useful for OpenRouter's dashboard.
 */
class OpenAiCompatibleProvider(
    private val prefs: UserPreferencesRepository,
    private val bundledDefaultKey: String,
    private val bundledDefaultBaseUrl: String,
    private val bundledDefaultModel: String,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : LLMProvider {

    override val name: String = "openai-compat"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private suspend fun resolveApiKey(): String? =
        prefs.apiKey.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: bundledDefaultKey.takeIf { it.isNotBlank() }

    private suspend fun resolveBaseUrl(): String =
        prefs.apiBaseUrl.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: bundledDefaultBaseUrl

    private suspend fun resolveModel(): String =
        prefs.modelName.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: bundledDefaultModel

    override fun generate(
        messages: List<LlmMessage>,
        temperature: Float,
    ): Flow<LlmChunk> = flow {
        val key = resolveApiKey() ?: run {
            emit(LlmChunk.Error("No API key configured — open Settings to add one.", retryable = false))
            return@flow
        }
        val endpoint = completionsUrl(resolveBaseUrl())
        val model = resolveModel()

        val body = buildJsonObject {
            put("model", model)
            put("temperature", temperature.toDouble())
            put("stream", true)
            putJsonArray("messages") {
                messages.forEach { m -> add(serializeMessage(m)) }
            }
        }
        streamCompletion(endpoint, key, body)
    }
        .flowOn(Dispatchers.IO)
        .catch { t -> emit(LlmChunk.Error(t.message ?: "Unknown LLM error", retryable = true)) }

    override suspend fun generateStructured(prompt: String, jsonSchema: String): String {
        val key = resolveApiKey() ?: error("No API key configured")
        val endpoint = completionsUrl(resolveBaseUrl())
        val model = resolveModel()

        val instructed = """
            You must respond with valid JSON conforming to this schema:
            $jsonSchema

            Return only the JSON, no Markdown fences, no prose.

            Task:
            $prompt
        """.trimIndent()

        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0.2)
            put("stream", false)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", instructed)
                })
            }
            putJsonObject("response_format") {
                put("type", "json_object")
            }
        }

        val request = buildRequest(endpoint, key, body)
        return httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}: $responseBody")
            val root = json.parseToJsonElement(responseBody).jsonObject
            val content = root["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?: error("upstream returned no content")
            content
        }
    }

    // ───────────────────────────────────── internals ─────────────────────────────────────

    private fun serializeMessage(m: LlmMessage): JsonObject = buildJsonObject {
        put("role", when (m.role) {
            LlmRole.SYSTEM -> "system"
            LlmRole.USER -> "user"
            LlmRole.ASSISTANT -> "assistant"
        })
        if (m.images.isEmpty()) {
            put("content", m.text)
        } else {
            putJsonArray("content") {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", m.text)
                })
                m.images.forEach { bytes ->
                    add(buildJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put("url", "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}")
                        }
                    })
                }
            }
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<LlmChunk>.streamCompletion(
        endpoint: String,
        key: String,
        body: JsonObject,
    ) {
        val request = buildRequest(endpoint, key, body)
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errText = response.body?.string().orEmpty().take(500)
                emit(LlmChunk.Error("HTTP ${response.code}: $errText", retryable = response.code >= 500))
                return@use
            }
            val source = response.body?.source()
            if (source == null) {
                emit(LlmChunk.Error("Empty response body", retryable = true))
                return@use
            }

            var gotAnyContent = false

            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isEmpty()) continue
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                if (payload == "[DONE]") break

                val root = try {
                    json.parseToJsonElement(payload).jsonObject
                } catch (t: Throwable) {
                    continue
                }

                val errorObj = root["error"] as? JsonObject
                if (errorObj != null) {
                    val msg = errorObj["message"]?.jsonPrimitive?.content ?: "upstream error"
                    emit(LlmChunk.Error(msg, retryable = false))
                    return@use
                }

                val delta = root["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("delta")
                    ?.jsonObject?.get("content")
                    ?.jsonPrimitive?.content

                if (!delta.isNullOrEmpty()) {
                    gotAnyContent = true
                    emit(LlmChunk.Text(delta))
                }
            }

            if (!gotAnyContent) {
                emit(LlmChunk.Error(
                    "no content received from upstream (model may be unavailable)",
                    retryable = true,
                ))
                return@use
            }
            emit(LlmChunk.Done(totalTokens = null))
        }
    }

    private fun buildRequest(endpoint: String, apiKey: String, body: JsonObject): Request =
        Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("HTTP-Referer", "https://github.com/ggggh123/Personal-Studio")
            .header("X-Title", "Personal-Studio")
            .post(json.encodeToString(JsonElement.serializer(), body).toRequestBody(JSON_MEDIA_TYPE))
            .build()

    private fun completionsUrl(baseUrl: String): String =
        baseUrl.trimEnd('/') + "/chat/completions"

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
