package com.example.personal_studio.data.remote.llm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.personal_studio.data.local.datastore.UserPreferencesRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

class GeminiProvider(
    private val prefs: UserPreferencesRepository,
    private val bundledDefaultKey: String,
    private val modelName: String = "gemini-1.5-flash",
) : LLMProvider {

    override val name: String = "gemini ($modelName)"

    private suspend fun resolveApiKey(): String? =
        prefs.geminiApiKey.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: bundledDefaultKey.takeIf { it.isNotBlank() }

    private suspend fun buildModel(temperature: Float): GenerativeModel? {
        val key = resolveApiKey() ?: return null
        return GenerativeModel(
            modelName = modelName,
            apiKey = key,
            generationConfig = generationConfig {
                this.temperature = temperature
            },
            safetySettings = listOf(
                SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH),
                SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH),
            ),
        )
    }

    override fun generateText(prompt: String, systemPrompt: String?, temperature: Float): Flow<LlmChunk> = flow {
        val model = buildModel(temperature) ?: run {
            emit(LlmChunk.Error("No API key configured — open Settings to add one.", retryable = false))
            return@flow
        }
        val full = buildString {
            if (!systemPrompt.isNullOrBlank()) appendLine(systemPrompt).appendLine()
            append(prompt)
        }
        model.generateContentStream(full).collect { resp ->
            resp.text?.takeIf { it.isNotEmpty() }?.let { emit(LlmChunk.Text(it)) }
        }
        emit(LlmChunk.Done(totalTokens = null))
    }.catch { t ->
        emit(LlmChunk.Error(t.message ?: "Unknown LLM error", retryable = true))
    }

    override fun generateMultimodal(prompt: String, images: List<ByteArray>, systemPrompt: String?): Flow<LlmChunk> = flow {
        val model = buildModel(temperature = 0.7f) ?: run {
            emit(LlmChunk.Error("No API key configured — open Settings to add one.", retryable = false))
            return@flow
        }
        val bitmaps = images.map { BitmapFactory.decodeByteArray(it, 0, it.size) }
        val contentMsg = content {
            if (!systemPrompt.isNullOrBlank()) text(systemPrompt)
            bitmaps.forEach { image(it) }
            text(prompt)
        }
        model.generateContentStream(contentMsg).collect { resp ->
            resp.text?.takeIf { it.isNotEmpty() }?.let { emit(LlmChunk.Text(it)) }
        }
        emit(LlmChunk.Done(totalTokens = null))
        bitmaps.forEach(Bitmap::recycle)
    }.catch { t ->
        emit(LlmChunk.Error(t.message ?: "Unknown LLM error", retryable = true))
    }

    override suspend fun generateStructured(prompt: String, jsonSchema: String): String {
        val model = buildModel(temperature = 0.2f)
            ?: throw IllegalStateException("No API key configured")
        val instructed = """
            You must respond with valid JSON conforming to this schema:
            $jsonSchema

            Return only the JSON, no Markdown fences, no prose.

            Task:
            $prompt
        """.trimIndent()
        val resp = model.generateContent(instructed)
        return resp.text ?: error("Gemini returned empty response")
    }
}
