package com.example.personal_studio.core.llm

/** The request format an endpoint speaks. [id] is what is stored. */
enum class ApiFormat(val id: String, val label: String) {
    /** `POST {base}/chat/completions`, Bearer key: OpenAI, DeepSeek, OpenRouter, 硅基流动, Ollama… */
    OPENAI("openai", "OpenAI 兼容"),
    /** `POST {base}/v1/messages`, `x-api-key`: Anthropic (Claude) and services that copy it. */
    ANTHROPIC("anthropic", "Anthropic"),
    /** `POST {base}/v1beta/models/{model}:generateContent`, `x-goog-api-key`: Google AI Studio. */
    GEMINI("gemini", "Google Gemini");

    companion object {
        fun of(id: String?): ApiFormat = entries.firstOrNull { it.id == id } ?: OPENAI
    }
}

/**
 * How hard the model thinks before answering. [AUTO] sends nothing and leaves it to the service;
 * the others are mapped onto each format's own parameter (see the wire formats).
 */
enum class ReasoningLevel(val id: String, val label: String) {
    AUTO("auto", "自动"),
    OFF("off", "关闭"),
    LOW("low", "低"),
    MEDIUM("medium", "中"),
    HIGH("high", "高"),
    MAX("max", "最高");

    companion object {
        fun of(id: String?): ReasoningLevel = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/**
 * Generation settings applied to every request. A null value means "not sent": the feature's own
 * temperature (0.7 for chat, 0.3 for structured output) and the service's defaults apply.
 */
data class GenerationSettings(
    val reasoning: ReasoningLevel = ReasoningLevel.AUTO,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val topP: Float? = null,
) {
    companion object {
        const val TEMPERATURE_MAX = 2f
        val MAX_TOKEN_CHOICES = listOf(4_096, 8_192, 16_384, 32_768, 65_536)
    }
}

/** Where and how a request goes. [custom] is false for the built-in DeepSeek endpoint. */
data class LlmEndpoint(
    val format: ApiFormat,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val custom: Boolean,
)

/** The endpoint compiled into the app (from local.properties). */
data class BundledLlm(val apiKey: String, val baseUrl: String, val model: String)

object LlmEndpoints {
    /** The official address of a format, used when a custom endpoint leaves the address empty. */
    fun defaultBaseUrl(format: ApiFormat, bundled: BundledLlm): String = when (format) {
        ApiFormat.OPENAI -> bundled.baseUrl
        ApiFormat.ANTHROPIC -> "https://api.anthropic.com"
        ApiFormat.GEMINI -> "https://generativelanguage.googleapis.com"
    }

    /**
     * The endpoint in use. Saving an address or a key makes the endpoint custom; an empty address
     * then means the format's official one. The built-in key and model only fill in for a custom
     * endpoint on the built-in host (e.g. DeepSeek's Anthropic address): the key is never sent
     * anywhere else. Without an address or key, only a built-in model can be used; an older saved
     * id (from the earlier built-in list) falls back to the default.
     */
    fun resolve(format: String?, apiKey: String?, baseUrl: String?, model: String?, bundled: BundledLlm): LlmEndpoint {
        val custom = !apiKey.isNullOrBlank() || !baseUrl.isNullOrBlank()
        if (!custom) {
            val code = CuratedModels.of(model)?.code ?: CuratedModels.of(bundled.model)?.code ?: CuratedModels.DEFAULT.code
            return LlmEndpoint(ApiFormat.OPENAI, bundled.baseUrl, bundled.apiKey, code, custom = false)
        }
        val apiFormat = ApiFormat.of(format)
        val base = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: defaultBaseUrl(apiFormat, bundled)
        val builtInHost = sameHost(base, bundled.baseUrl)
        return LlmEndpoint(
            format = apiFormat,
            baseUrl = base,
            apiKey = apiKey?.trim()?.takeIf { it.isNotEmpty() } ?: bundled.apiKey.takeIf { builtInHost }.orEmpty(),
            model = model?.trim()?.takeIf { it.isNotEmpty() } ?: bundled.model.takeIf { builtInHost }.orEmpty(),
            custom = true,
        )
    }

    fun host(url: String): String? = try {
        java.net.URI(url.trim()).host?.lowercase()
    } catch (_: Exception) {
        null
    }

    private fun sameHost(a: String, b: String): Boolean = host(a)?.let { it == host(b) } == true

    /**
     * The model for one request: a built-in text-only model hands image questions to the built-in
     * model that reads images, so a photo never fails just because Pro is selected.
     */
    fun modelFor(endpoint: LlmEndpoint, hasImages: Boolean): String {
        if (endpoint.custom || !hasImages) return endpoint.model
        val chosen = CuratedModels.of(endpoint.model) ?: return endpoint.model
        return if (chosen.readsImages) endpoint.model else CuratedModels.ALL.first { it.readsImages }.code
    }
}
