package com.example.personal_studio.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CuratedModelsTest {
    private val bundled = BundledLlm("sk-built-in", "https://api.deepseek.com", "deepseek-flash")

    @Test fun `only DeepSeek's two models are built in`() {
        assertEquals(listOf("deepseek-flash", "deepseek-v4-pro"), CuratedModels.ALL.map { it.code })
        assertTrue(CuratedModels.FLASH.readsImages)
        assertFalse(CuratedModels.PRO.readsImages)
        assertEquals("DeepSeek Flash", CuratedModels.displayFor("deepseek-flash"))
        // DeepSeek still serves the retired Flash names.
        assertEquals("DeepSeek Flash", CuratedModels.displayFor("deepseek-v4-flash"))
        assertNull(CuratedModels.displayFor("some-custom-model"))
        assertNull(CuratedModels.displayFor(null))
    }

    @Test fun `without an address or key the built-in endpoint is used, with a built-in model`() {
        val e = LlmEndpoints.resolve(format = "anthropic", apiKey = null, baseUrl = null, model = "deepseek-v4-pro", bundled = bundled)
        assertEquals(LlmEndpoint(ApiFormat.OPENAI, "https://api.deepseek.com", "sk-built-in", "deepseek-v4-pro", custom = false), e)
        // A model from the earlier built-in list cannot run on DeepSeek: back to the default.
        assertEquals("deepseek-flash", LlmEndpoints.resolve(null, null, null, "claude-opus-4-8", bundled).model)
    }

    @Test fun `a custom address on another host never receives the built-in key`() {
        val e = LlmEndpoints.resolve("openai", apiKey = null, baseUrl = "https://openrouter.ai/api/v1", model = "x/y", bundled = bundled)
        assertTrue(e.custom)
        assertEquals("", e.apiKey)
        assertEquals("x/y", e.model)
        val noModel = LlmEndpoints.resolve("openai", apiKey = "sk-own", baseUrl = "https://openrouter.ai/api/v1", model = null, bundled = bundled)
        assertEquals("", noModel.model)
    }

    @Test fun `DeepSeek's Anthropic address can use the built-in key and model`() {
        val e = LlmEndpoints.resolve("anthropic", apiKey = null, baseUrl = "https://api.deepseek.com/anthropic", model = null, bundled = bundled)
        assertEquals(ApiFormat.ANTHROPIC, e.format)
        assertEquals("sk-built-in", e.apiKey)
        assertEquals("deepseek-flash", e.model)
    }

    @Test fun `an empty address means the format's official one`() {
        val claude = LlmEndpoints.resolve("anthropic", apiKey = "sk-ant", baseUrl = null, model = "claude-sonnet-4-6", bundled = bundled)
        assertEquals("https://api.anthropic.com", claude.baseUrl)
        assertEquals("sk-ant", claude.apiKey)
        val gemini = LlmEndpoints.resolve("gemini", apiKey = "AIza", baseUrl = " ", model = "gemini-3.5-flash", bundled = bundled)
        assertEquals("https://generativelanguage.googleapis.com", gemini.baseUrl)
    }

    @Test fun `built-in Pro hands image questions to Flash`() {
        val pro = LlmEndpoints.resolve(null, null, null, "deepseek-v4-pro", bundled)
        assertEquals("deepseek-v4-pro", LlmEndpoints.modelFor(pro, hasImages = false))
        assertEquals("deepseek-flash", LlmEndpoints.modelFor(pro, hasImages = true))
        val custom = LlmEndpoints.resolve("openai", "sk-own", "https://api.deepseek.com", "deepseek-v4-pro", bundled)
        assertEquals("deepseek-v4-pro", LlmEndpoints.modelFor(custom, hasImages = true))
    }
}
