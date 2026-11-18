package com.example.personal_studio.data.remote.llm

import com.example.personal_studio.core.llm.BundledLlm
import com.example.personal_studio.core.llm.GenerationSettings
import com.example.personal_studio.core.llm.ReasoningLevel
import com.example.personal_studio.data.local.datastore.FakeUserPreferencesRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Real HTTP against a local server: nothing leaves the machine. */
class ConfigurableLlmProviderTest {
    private val server = MockWebServer()
    private lateinit var bundled: BundledLlm

    @Before fun setUp() {
        server.start()
        bundled = BundledLlm("sk-built-in", server.url("/").toString().trimEnd('/'), "deepseek-flash")
    }

    @After fun tearDown() = server.shutdown()

    private fun sse(vararg lines: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(lines.joinToString("") { "$it\n\n" })

    private fun taken(): Pair<okhttp3.mockwebserver.RecordedRequest, JsonObject> {
        val r = server.takeRequest(2, TimeUnit.SECONDS)!!
        return r to (Json.parseToJsonElement(r.body.readUtf8()) as JsonObject)
    }

    private suspend fun custom(format: String?, model: String = "m", settings: GenerationSettings = GenerationSettings()) =
        FakeUserPreferencesRepository().apply {
            setApiFormat(format)
            setApiBaseUrl(server.url("/").toString().trimEnd('/'))
            setApiKey("sk-own")
            setModelName(model)
            setGeneration(settings)
        }

    @Test fun `built-in endpoint streams OpenAI deltas and sends the reasoning level`() = runBlocking {
        server.enqueue(sse(
            """data: {"choices":[{"delta":{"reasoning_content":"thinking"}}]}""",
            """data: {"choices":[{"delta":{"content":"po"}}]}""",
            """data: {"choices":[{"delta":{"content":"ng"}}]}""",
            "data: [DONE]",
        ))
        val prefs = FakeUserPreferencesRepository().apply { setGeneration(GenerationSettings(ReasoningLevel.LOW)) }
        val chunks = ConfigurableLlmProvider(prefs, bundled).generateText("ping").toList()

        assertEquals(listOf(LlmChunk.Text("po"), LlmChunk.Text("ng"), LlmChunk.Done(null, "deepseek-flash")), chunks)
        val (request, body) = taken()
        assertEquals("/chat/completions", request.path)
        assertEquals("Bearer sk-built-in", request.getHeader("Authorization"))
        assertEquals("low", body["reasoning_effort"]!!.jsonPrimitive.content)
    }

    @Test fun `built-in Pro sends a photo question to Flash`() = runBlocking {
        server.enqueue(sse("""data: {"choices":[{"delta":{"content":"a cat"}}]}""", "data: [DONE]"))
        val prefs = FakeUserPreferencesRepository().apply { setModelName("deepseek-v4-pro") }
        val chunks = ConfigurableLlmProvider(prefs, bundled).generateMultimodal("what", listOf(byteArrayOf(1, 2, 3))).toList()
        assertEquals(LlmChunk.Done(null, "deepseek-flash"), chunks.last())
        assertEquals("deepseek-flash", taken().second["model"]!!.jsonPrimitive.content)
    }

    @Test fun `Anthropic stream reads text deltas and stops at message_stop`() = runBlocking {
        server.enqueue(sse(
            "event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"id\":\"m1\"}}",
            "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"x\"}}",
            "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}",
            "event: message_stop\ndata: {\"type\":\"message_stop\"}",
        ))
        val provider = ConfigurableLlmProvider(custom("anthropic", "claude-sonnet-4-6", GenerationSettings(ReasoningLevel.HIGH)), bundled)
        val chunks = provider.generateText("hi", systemPrompt = "be brief").toList()

        assertEquals(listOf(LlmChunk.Text("Hello"), LlmChunk.Done(null, "claude-sonnet-4-6")), chunks)
        val (request, body) = taken()
        assertEquals("/v1/messages", request.path)
        assertEquals("sk-own", request.getHeader("x-api-key"))
        assertEquals("be brief", body["system"]!!.jsonPrimitive.content)
    }

    @Test fun `Gemini stream goes to the model's streaming method`() = runBlocking {
        server.enqueue(sse(
            """data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Hel"}]}}]}""",
            """data: {"candidates":[{"content":{"role":"model","parts":[{"text":"lo"}]},"finishReason":"STOP"}]}""",
        ))
        val chunks = ConfigurableLlmProvider(custom("gemini", "gemini-3.5-flash"), bundled).generateText("hi").toList()
        assertEquals(listOf(LlmChunk.Text("Hel"), LlmChunk.Text("lo"), LlmChunk.Done(null, "gemini-3.5-flash")), chunks)
        val (request, _) = taken()
        assertEquals("/v1beta/models/gemini-3.5-flash:streamGenerateContent?alt=sse", request.path)
        assertEquals("sk-own", request.getHeader("x-goog-api-key"))
    }

    @Test fun `an HTTP error shows the service's own message`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Authentication Fails"}}"""))
        val chunks = ConfigurableLlmProvider(custom("openai"), bundled).generateText("hi").toList()
        assertEquals(listOf(LlmChunk.Error("HTTP 401: Authentication Fails", retryable = false)), chunks)
    }

    @Test fun `without a key nothing is sent and the person is told where to fix it`() = runBlocking {
        val prefs = FakeUserPreferencesRepository().apply { setApiBaseUrl("https://openrouter.ai/api/v1"); setModelName("x/y") }
        val chunks = ConfigurableLlmProvider(prefs, bundled).generateText("hi").toList()
        assertTrue((chunks.single() as LlmChunk.Error).message.contains("API 密钥"))
        assertEquals(0, server.requestCount)
    }

    @Test fun `structured output from Anthropic comes back without fences`() = runBlocking {
        server.enqueue(MockResponse().setBody(
            """{"content":[{"type":"text","text":"```json\n{\"ok\":true}\n```"}]}""",
        ))
        val provider = ConfigurableLlmProvider(custom("anthropic", "claude-sonnet-4-6"), bundled)
        assertEquals("""{"ok":true}""", provider.generateStructured(listOf(LlmMessage(LlmRole.USER, "go")), """{"type":"object"}"""))
        val (_, body) = taken()
        assertEquals("false", body["stream"]!!.jsonPrimitive.content)
        assertTrue(body["system"]!!.jsonPrimitive.content.contains("valid JSON"))
    }
}
