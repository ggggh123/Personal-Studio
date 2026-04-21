package com.example.personal_studio.data.remote.llm

import android.util.Base64
import com.example.personal_studio.data.local.datastore.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
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
 * OpenRouter (OpenAI-compatible) LLM provider.
 *
 * Endpoint: https://openrouter.ai/api/v1/chat/completions
 *
 * Supports streaming via SSE. The response body is a sequence of `data: {json}` lines
 * terminated by `data: [DONE]`. Each chunk's `choices[0].delta.content` is the text delta.
 *
 * Multimodal is done via OpenAI's array-of-parts format: the user message's `content`
 * becomes a list of `{type:"text"}` and `{type:"image_url"}` objects with base64 data URIs.
 */
class OpenRouterProvider(
    private val prefs: UserPreferencesRepository,
    private val bundledDefaultKey: String,
    private val bundledDefaultModel: String,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : LLMProvider {

    override val name: String = "openrouter"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private suspend fun resolveApiKey(): String? =
        prefs.openRouterApiKey.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: bundledDefaultKey.takeIf { it.isNotBlank() }

    private suspend fun resolveModel(): String =
        prefs.modelName.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: bundledDefaultModel

    override fun generateText(
        prompt: String,
        systemPrompt: String?,
        temperature: Float,
    ): Flow<LlmChunk> = flow {
        val key = resolveApiKey() ?: run {
            emit(LlmChunk.Error("No API key configured — open Settings to add one.", retryable = false))
            return@flow
        }
        val model = resolveModel()

        val body = buildJsonObject {
            put("model", model)
            put("temperature", temperature.toDouble())
            put("stream", true)
            putJsonArray("messages") {
                if (!systemPrompt.isNullOrBlank()) {
                    add(textMessage("system", systemPrompt))
                }
                add(textMessage("user", prompt))
            }
        }

        streamCompletion(key, body)
    }
        .flowOn(Dispatchers.IO)
        .catch { t -> emit(LlmChunk.Error(t.message ?: "Unknown LLM error", retryable = true)) }

    override fun generateMultimodal(
        prompt: String,
        images: List<ByteArray>,
        systemPrompt: String?,
        temperature: Float,
    ): Flow<LlmChunk> = flow {
        val key = resolveApiKey() ?: run {
            emit(LlmChunk.Error("No API key configured — open Settings to add one.", retryable = false))
            return@flow
        }
        val model = resolveModel()

        val body = buildJsonObject {
            put("model", model)
            put("temperature", temperature.toDouble())
            put("stream", true)
            putJsonArray("messages") {
                if (!systemPrompt.isNullOrBlank()) {
                    add(textMessage("system", systemPrompt))
                }
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        })
                        images.forEach { bytes ->
                            add(buildJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}")
                                }
                            })
                        }
                    }
                })
            }
        }

        streamCompletion(key, body)
    }
        .flowOn(Dispatchers.IO)
        .catch { t -> emit(LlmChunk.Error(t.message ?: "Unknown LLM error", retryable = true)) }

    override suspend fun generateStructured(prompt: String, jsonSchema: String): String {
        val key = resolveApiKey() ?: error("No API key configured")
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
                add(textMessage("user", instructed))
            }
            // Ask for JSON mode if the model supports it
            putJsonObject("response_format") {
                put("type", "json_object")
            }
        }

        val request = buildRequest(key, body)
        return httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}: $responseBody")
            val root = json.parseToJsonElement(responseBody).jsonObject
            val content = root["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?: error("OpenRouter returned no content")
            content
        }
    }

    // ───────────────────────────────────── internals ─────────────────────────────────────

    private suspend fun kotlinx.coroutines.flow.FlowCollector<LlmChunk>.streamCompletion(
        key: String,
        body: JsonObject,
    ) {
        val request = buildRequest(key, body)
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

            // readUtf8Line() is null-safe at EOF (unlike strict). Loop until the stream
            // closes or [DONE] / error marker arrives.
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isEmpty()) continue                 // SSE event terminator
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                if (payload == "[DONE]") break

                val root = try {
                    json.parseToJsonElement(payload).jsonObject
                } catch (t: Throwable) {
                    continue
                }

                // OpenAI-compatible API sometimes returns errors inside the stream
                // with HTTP 200. Handle that explicitly.
                val errorObj = root["error"]?.let { it as? JsonObject ?: (it as? kotlinx.serialization.json.JsonElement)?.jsonObject }
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

    private fun buildRequest(apiKey: String, body: JsonObject): Request =
        Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("HTTP-Referer", "https://github.com/ggggh123/Personal-Studio")
            .header("X-Title", "Personal-Studio")
            .post(json.encodeToString(JsonElement.serializer(), body).toRequestBody(JSON_MEDIA_TYPE))
            .build()

    companion object {
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)       // Streaming can be slow-trickle
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

        private fun textMessage(role: String, content: String): JsonObject =
            buildJsonObject {
                put("role", role)
                put("content", content)
            }
    }
}
