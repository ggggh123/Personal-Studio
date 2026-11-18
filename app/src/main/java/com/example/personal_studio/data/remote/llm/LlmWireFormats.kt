package com.example.personal_studio.data.remote.llm

import com.example.personal_studio.core.llm.ApiFormat
import com.example.personal_studio.core.llm.GenerationSettings
import com.example.personal_studio.core.llm.LlmEndpoint
import com.example.personal_studio.core.llm.ReasoningLevel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/** What one streamed SSE `data:` payload means. */
internal sealed interface StreamEvent {
    data class Text(val delta: String) : StreamEvent
    data class Failed(val message: String) : StreamEvent
    object End : StreamEvent
    object Skip : StreamEvent
}

/** One API format: where the request goes, how it is built, and how replies are read. */
internal interface LlmWireFormat {
    fun url(endpoint: LlmEndpoint, model: String, stream: Boolean): String
    fun headers(endpoint: LlmEndpoint, stream: Boolean): Map<String, String>

    /** [temperature] is the feature's own; [settings] override it and add what the person chose. */
    fun body(
        model: String, messages: List<LlmMessage>, temperature: Float,
        settings: GenerationSettings, stream: Boolean, jsonOutput: Boolean,
    ): JsonObject

    fun event(payload: JsonObject): StreamEvent

    /** The answer text of a non-streamed reply. */
    fun text(reply: JsonObject): String?

    companion object {
        fun of(format: ApiFormat): LlmWireFormat = when (format) {
            ApiFormat.OPENAI -> OpenAiChatFormat
            ApiFormat.ANTHROPIC -> AnthropicMessagesFormat
            ApiFormat.GEMINI -> GeminiFormat
        }

        /** A readable message from an error body, whichever format wrote it. */
        fun errorMessage(body: JsonObject?): String? {
            val error = body?.get("error")
            (error as? JsonObject)?.get("message")?.str()?.let { return it }
            (error as? JsonPrimitive)?.contentOrNull?.let { return it }
            return body?.get("message")?.str()
        }
    }
}

// ───────────────────────────────── shared helpers ─────────────────────────────────

internal fun Any?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

/** GLM-style strict servers reject temperatures with more than two decimals. */
internal fun twoDecimals(value: Float): Double = kotlin.math.round(value.toDouble() * 100.0) / 100.0

internal fun mimeOf(bytes: ByteArray): String = when {
    bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() -> "image/png"
    bytes.size > 3 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() -> "image/webp"
    else -> "image/jpeg"
}

internal fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

private fun JsonObject.obj(key: String) = this[key] as? JsonObject
private fun JsonObject.arr(key: String) = this[key] as? JsonArray

// ───────────────────────────────── OpenAI chat completions ─────────────────────────────────

/**
 * `POST {base}/chat/completions`. Reasoning: 关闭 sends `thinking: {type: disabled}` (DeepSeek and
 * others that copy it); 低–最高 send `reasoning_effort` (DeepSeek maps 中 to 高).
 */
internal object OpenAiChatFormat : LlmWireFormat {
    override fun url(endpoint: LlmEndpoint, model: String, stream: Boolean): String {
        val base = endpoint.baseUrl.trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    override fun headers(endpoint: LlmEndpoint, stream: Boolean) = mapOf(
        "Authorization" to "Bearer ${endpoint.apiKey}",
        "Accept" to if (stream) "text/event-stream" else "application/json",
        // OpenRouter shows these on its dashboard; other servers ignore them.
        "HTTP-Referer" to "https://github.com/ggggh123/Personal-Studio",
        "X-Title" to "Kejian",
    )

    override fun body(
        model: String, messages: List<LlmMessage>, temperature: Float,
        settings: GenerationSettings, stream: Boolean, jsonOutput: Boolean,
    ) = buildJsonObject {
        put("model", model)
        put("stream", stream)
        put("temperature", twoDecimals(settings.temperature ?: temperature))
        settings.topP?.let { put("top_p", twoDecimals(it)) }
        settings.maxTokens?.let { put("max_tokens", it) }
        when (settings.reasoning) {
            ReasoningLevel.AUTO -> Unit
            ReasoningLevel.OFF -> putJsonObject("thinking") { put("type", "disabled") }
            else -> put("reasoning_effort", settings.reasoning.id)
        }
        if (jsonOutput) putJsonObject("response_format") { put("type", "json_object") }
        putJsonArray("messages") {
            messages.forEach { m ->
                add(buildJsonObject {
                    put("role", when (m.role) {
                        LlmRole.SYSTEM -> "system"
                        LlmRole.USER -> "user"
                        LlmRole.ASSISTANT -> "assistant"
                    })
                    if (m.images.isEmpty()) put("content", m.text)
                    else putJsonArray("content") {
                        add(buildJsonObject { put("type", "text"); put("text", m.text) })
                        m.images.forEach { bytes ->
                            add(buildJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") { put("url", "data:${mimeOf(bytes)};base64,${base64(bytes)}") }
                            })
                        }
                    }
                })
            }
        }
    }

    override fun event(payload: JsonObject): StreamEvent {
        LlmWireFormat.errorMessage(payload)?.takeIf { payload["error"] != null }?.let { return StreamEvent.Failed(it) }
        val delta = (payload.arr("choices")?.firstOrNull() as? JsonObject)?.obj("delta")
        // Thinking arrives as `reasoning_content`; only the answer is shown.
        return delta?.get("content").str()?.takeIf { it.isNotEmpty() }?.let { StreamEvent.Text(it) } ?: StreamEvent.Skip
    }

    override fun text(reply: JsonObject): String? =
        ((reply.arr("choices")?.firstOrNull() as? JsonObject)?.obj("message"))?.get("content").str()
}

// ───────────────────────────────── Anthropic messages ─────────────────────────────────

/**
 * `POST {base}/v1/messages` with `x-api-key`. `max_tokens` is required, so it defaults to
 * [DEFAULT_MAX_TOKENS]. Reasoning: 关闭 sends `thinking: {type: disabled}`; 低–最高 send
 * `output_config.effort` (Claude and DeepSeek). Newer Claude models refuse temperature and top-p
 * together, so a set top-p replaces the temperature.
 */
internal object AnthropicMessagesFormat : LlmWireFormat {
    const val DEFAULT_MAX_TOKENS = 32_000

    override fun url(endpoint: LlmEndpoint, model: String, stream: Boolean): String {
        val base = endpoint.baseUrl.trimEnd('/')
        return when {
            base.endsWith("/messages") -> base
            base.endsWith("/v1") -> "$base/messages"
            else -> "$base/v1/messages"
        }
    }

    override fun headers(endpoint: LlmEndpoint, stream: Boolean) = mapOf(
        "x-api-key" to endpoint.apiKey,
        "anthropic-version" to "2023-06-01",
        "Accept" to if (stream) "text/event-stream" else "application/json",
    )

    override fun body(
        model: String, messages: List<LlmMessage>, temperature: Float,
        settings: GenerationSettings, stream: Boolean, jsonOutput: Boolean,
    ) = buildJsonObject {
        put("model", model)
        put("max_tokens", settings.maxTokens ?: DEFAULT_MAX_TOKENS)
        put("stream", stream)
        val topP = settings.topP
        if (topP != null) put("top_p", twoDecimals(topP))
        else put("temperature", twoDecimals((settings.temperature ?: temperature).coerceAtMost(1f)))
        when (settings.reasoning) {
            ReasoningLevel.AUTO -> Unit
            ReasoningLevel.OFF -> putJsonObject("thinking") { put("type", "disabled") }
            else -> putJsonObject("output_config") { put("effort", settings.reasoning.id) }
        }
        val system = messages.filter { it.role == LlmRole.SYSTEM }.joinToString("\n\n") { it.text }
        if (system.isNotBlank()) put("system", system)
        putJsonArray("messages") {
            messages.filter { it.role != LlmRole.SYSTEM }.forEach { m ->
                add(buildJsonObject {
                    put("role", if (m.role == LlmRole.ASSISTANT) "assistant" else "user")
                    if (m.images.isEmpty()) put("content", m.text)
                    else putJsonArray("content") {
                        m.images.forEach { bytes ->
                            add(buildJsonObject {
                                put("type", "image")
                                putJsonObject("source") {
                                    put("type", "base64")
                                    put("media_type", mimeOf(bytes))
                                    put("data", base64(bytes))
                                }
                            })
                        }
                        add(buildJsonObject { put("type", "text"); put("text", m.text) })
                    }
                })
            }
        }
    }

    override fun event(payload: JsonObject): StreamEvent = when (payload["type"].str()) {
        "content_block_delta" -> {
            val delta = payload.obj("delta")
            if (delta?.get("type").str() == "text_delta") delta?.get("text").str()?.let { StreamEvent.Text(it) } ?: StreamEvent.Skip
            else StreamEvent.Skip // thinking_delta, signature_delta…
        }
        "error" -> StreamEvent.Failed(LlmWireFormat.errorMessage(payload) ?: "upstream error")
        "message_stop" -> StreamEvent.End
        else -> StreamEvent.Skip
    }

    override fun text(reply: JsonObject): String? = reply.arr("content")
        ?.mapNotNull { block -> (block as? JsonObject)?.takeIf { it["type"].str() == "text" }?.get("text").str() }
        ?.joinToString("")
}

// ───────────────────────────────── Google Gemini ─────────────────────────────────

/**
 * `POST {base}/v1beta/models/{model}:streamGenerateContent?alt=sse` with `x-goog-api-key`.
 * Reasoning: 关闭 sends `thinkingBudget: 0`; 低–高 send `thinkingLevel` (最高 is 高).
 */
internal object GeminiFormat : LlmWireFormat {
    override fun url(endpoint: LlmEndpoint, model: String, stream: Boolean): String {
        val base = endpoint.baseUrl.trimEnd('/')
        val root = if (Regex("/v1(beta|alpha)?$").containsMatchIn(base)) base else "$base/v1beta"
        val id = model.removePrefix("models/")
        return if (stream) "$root/models/$id:streamGenerateContent?alt=sse" else "$root/models/$id:generateContent"
    }

    override fun headers(endpoint: LlmEndpoint, stream: Boolean) = mapOf(
        "x-goog-api-key" to endpoint.apiKey,
        "Accept" to if (stream) "text/event-stream" else "application/json",
    )

    override fun body(
        model: String, messages: List<LlmMessage>, temperature: Float,
        settings: GenerationSettings, stream: Boolean, jsonOutput: Boolean,
    ) = buildJsonObject {
        val system = messages.filter { it.role == LlmRole.SYSTEM }.joinToString("\n\n") { it.text }
        if (system.isNotBlank()) putJsonObject("systemInstruction") {
            putJsonArray("parts") { add(buildJsonObject { put("text", system) }) }
        }
        putJsonArray("contents") {
            messages.filter { it.role != LlmRole.SYSTEM }.forEach { m ->
                add(buildJsonObject {
                    put("role", if (m.role == LlmRole.ASSISTANT) "model" else "user")
                    putJsonArray("parts") {
                        m.images.forEach { bytes ->
                            add(buildJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", mimeOf(bytes))
                                    put("data", base64(bytes))
                                }
                            })
                        }
                        add(buildJsonObject { put("text", m.text) })
                    }
                })
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", twoDecimals(settings.temperature ?: temperature))
            settings.topP?.let { put("topP", twoDecimals(it)) }
            settings.maxTokens?.let { put("maxOutputTokens", it) }
            if (jsonOutput) put("responseMimeType", "application/json")
            when (settings.reasoning) {
                ReasoningLevel.AUTO -> Unit
                ReasoningLevel.OFF -> putJsonObject("thinkingConfig") { put("thinkingBudget", 0) }
                else -> putJsonObject("thinkingConfig") {
                    put("thinkingLevel", if (settings.reasoning == ReasoningLevel.MAX) "high" else settings.reasoning.id)
                }
            }
        }
    }

    override fun event(payload: JsonObject): StreamEvent {
        if (payload["error"] != null) return StreamEvent.Failed(LlmWireFormat.errorMessage(payload) ?: "upstream error")
        return text(payload)?.takeIf { it.isNotEmpty() }?.let { StreamEvent.Text(it) } ?: StreamEvent.Skip
    }

    /** Answer parts of the first candidate; thought summaries (`thought: true`) are left out. */
    override fun text(reply: JsonObject): String? {
        val parts = ((reply.arr("candidates")?.firstOrNull() as? JsonObject)?.obj("content"))?.arr("parts") ?: return null
        return parts.mapNotNull { part ->
            (part as? JsonObject)?.takeIf { (it["thought"] as? JsonPrimitive)?.contentOrNull != "true" }?.get("text").str()
        }.joinToString("")
    }
}
