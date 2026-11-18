package com.example.personal_studio.data.remote.llm

import com.example.personal_studio.core.llm.ApiFormat
import com.example.personal_studio.core.llm.GenerationSettings
import com.example.personal_studio.core.llm.LlmEndpoint
import com.example.personal_studio.core.llm.ReasoningLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmWireFormatsTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2)
    private val chat = listOf(
        LlmMessage(LlmRole.SYSTEM, "be brief"),
        LlmMessage(LlmRole.USER, "what is this", images = listOf(png)),
        LlmMessage(LlmRole.ASSISTANT, "a graph"),
    )
    private fun endpoint(format: ApiFormat, base: String) = LlmEndpoint(format, base, "k", "m", custom = true)
    private fun obj(text: String) = Json.parseToJsonElement(text) as JsonObject

    @Test fun `addresses get each format's path`() {
        assertEquals("https://api.deepseek.com/chat/completions", OpenAiChatFormat.url(endpoint(ApiFormat.OPENAI, "https://api.deepseek.com/"), "m", true))
        assertEquals("https://api.anthropic.com/v1/messages", AnthropicMessagesFormat.url(endpoint(ApiFormat.ANTHROPIC, "https://api.anthropic.com"), "m", true))
        assertEquals("https://api.deepseek.com/anthropic/v1/messages", AnthropicMessagesFormat.url(endpoint(ApiFormat.ANTHROPIC, "https://api.deepseek.com/anthropic"), "m", true))
        assertEquals("https://x.dev/v1/messages", AnthropicMessagesFormat.url(endpoint(ApiFormat.ANTHROPIC, "https://x.dev/v1"), "m", true))
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:streamGenerateContent?alt=sse",
            GeminiFormat.url(endpoint(ApiFormat.GEMINI, "https://generativelanguage.googleapis.com"), "models/gemini-3.5-flash", true),
        )
        assertEquals("https://g.dev/v1/models/g:generateContent", GeminiFormat.url(endpoint(ApiFormat.GEMINI, "https://g.dev/v1"), "g", false))
    }

    @Test fun `each format puts its key in its own header`() {
        assertEquals("Bearer k", OpenAiChatFormat.headers(endpoint(ApiFormat.OPENAI, "https://a"), true)["Authorization"])
        val anthropic = AnthropicMessagesFormat.headers(endpoint(ApiFormat.ANTHROPIC, "https://a"), true)
        assertEquals("k", anthropic["x-api-key"])
        assertEquals("2023-06-01", anthropic["anthropic-version"])
        assertNull(anthropic["Authorization"])
        assertEquals("k", GeminiFormat.headers(endpoint(ApiFormat.GEMINI, "https://a"), true)["x-goog-api-key"])
    }

    @Test fun `OpenAI body carries the settings and image parts`() {
        val auto = OpenAiChatFormat.body("m", chat, 0.7f, GenerationSettings(), stream = true, jsonOutput = false)
        assertEquals(0.7, auto["temperature"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertNull(auto["reasoning_effort"]); assertNull(auto["thinking"]); assertNull(auto["max_tokens"])
        val content = auto["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertTrue(content[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content.startsWith("data:image/png;base64,"))

        val tuned = OpenAiChatFormat.body(
            "m", chat, 0.7f, GenerationSettings(ReasoningLevel.MAX, temperature = 0.2f, maxTokens = 8192, topP = 0.9f),
            stream = false, jsonOutput = true,
        )
        assertEquals("max", tuned["reasoning_effort"]!!.jsonPrimitive.content)
        assertEquals("0.2", tuned["temperature"]!!.jsonPrimitive.content)
        assertEquals("8192", tuned["max_tokens"]!!.jsonPrimitive.content)
        assertEquals("0.9", tuned["top_p"]!!.jsonPrimitive.content)
        assertEquals("json_object", tuned["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)

        val off = OpenAiChatFormat.body("m", chat, 0.7f, GenerationSettings(ReasoningLevel.OFF), stream = true, jsonOutput = false)
        assertEquals("disabled", off["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertNull(off["reasoning_effort"])
    }

    @Test fun `Anthropic body lifts the system prompt and always has max_tokens`() {
        val body = AnthropicMessagesFormat.body("m", chat, 0.7f, GenerationSettings(ReasoningLevel.HIGH), stream = true, jsonOutput = false)
        assertEquals("be brief", body["system"]!!.jsonPrimitive.content)
        assertEquals(AnthropicMessagesFormat.DEFAULT_MAX_TOKENS.toString(), body["max_tokens"]!!.jsonPrimitive.content)
        assertEquals("high", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray
        assertEquals(listOf("user", "assistant"), messages.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        val image = messages[0].jsonObject["content"]!!.jsonArray[0].jsonObject
        assertEquals("image/png", image["source"]!!.jsonObject["media_type"]!!.jsonPrimitive.content)
        // Top-p set: no temperature (newer Claude models refuse both).
        val topP = AnthropicMessagesFormat.body("m", chat, 0.7f, GenerationSettings(topP = 0.8f, temperature = 0.5f), stream = true, jsonOutput = false)
        assertNull(topP["temperature"])
        assertEquals("0.8", topP["top_p"]!!.jsonPrimitive.content)
        val off = AnthropicMessagesFormat.body("m", chat, 0.7f, GenerationSettings(ReasoningLevel.OFF), stream = true, jsonOutput = false)
        assertEquals("disabled", off["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun `Gemini body uses contents, model role and thinking config`() {
        val body = GeminiFormat.body("m", chat, 0.3f, GenerationSettings(ReasoningLevel.OFF, maxTokens = 4096), stream = true, jsonOutput = true)
        assertEquals("be brief", body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        val contents = body["contents"]!!.jsonArray
        assertEquals(listOf("user", "model"), contents.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        assertEquals("image/png", contents[0].jsonObject["parts"]!!.jsonArray[0].jsonObject["inline_data"]!!.jsonObject["mime_type"]!!.jsonPrimitive.content)
        val config = body["generationConfig"]!!.jsonObject
        assertEquals("0", config["thinkingConfig"]!!.jsonObject["thinkingBudget"]!!.jsonPrimitive.content)
        assertEquals("4096", config["maxOutputTokens"]!!.jsonPrimitive.content)
        assertEquals("application/json", config["responseMimeType"]!!.jsonPrimitive.content)
        val max = GeminiFormat.body("m", chat, 0.3f, GenerationSettings(ReasoningLevel.MAX), stream = true, jsonOutput = false)
        assertEquals("high", max["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject["thinkingLevel"]!!.jsonPrimitive.content)
    }

    @Test fun `stream events give answer text and skip thinking`() {
        assertEquals(StreamEvent.Text("Hi"), OpenAiChatFormat.event(obj("""{"choices":[{"delta":{"content":"Hi"}}]}""")))
        assertEquals(StreamEvent.Skip, OpenAiChatFormat.event(obj("""{"choices":[{"delta":{"reasoning_content":"hmm"}}]}""")))
        assertEquals(StreamEvent.Failed("quota"), OpenAiChatFormat.event(obj("""{"error":{"message":"quota"}}""")))

        assertEquals(StreamEvent.Text("Hi"), AnthropicMessagesFormat.event(obj("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}""")))
        assertEquals(StreamEvent.Skip, AnthropicMessagesFormat.event(obj("""{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"hmm"}}""")))
        assertEquals(StreamEvent.End, AnthropicMessagesFormat.event(obj("""{"type":"message_stop"}""")))
        assertEquals(StreamEvent.Failed("overloaded"), AnthropicMessagesFormat.event(obj("""{"type":"error","error":{"type":"overloaded_error","message":"overloaded"}}""")))

        assertEquals(StreamEvent.Text("Hi"), GeminiFormat.event(obj(
            """{"candidates":[{"content":{"role":"model","parts":[{"text":"plan","thought":true},{"text":"Hi"}]}}]}""")))
        assertEquals(StreamEvent.Skip, GeminiFormat.event(obj("""{"candidates":[{"content":{"parts":[{"text":"plan","thought":true}]}}]}""")))
    }

    @Test fun `whole replies give their text`() {
        assertEquals("{}", OpenAiChatFormat.text(obj("""{"choices":[{"message":{"content":"{}"}}]}""")))
        assertEquals("ab", AnthropicMessagesFormat.text(obj("""{"content":[{"type":"thinking","thinking":"x"},{"type":"text","text":"a"},{"type":"text","text":"b"}]}""")))
        assertEquals("ok", GeminiFormat.text(obj("""{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""")))
        assertFalse(ConfigurableLlmProvider.stripFences("```json\n{\"a\":1}\n```").contains("`"))
        assertEquals("{\"a\":1}", ConfigurableLlmProvider.stripFences("```json\n{\"a\":1}\n```"))
    }
}
