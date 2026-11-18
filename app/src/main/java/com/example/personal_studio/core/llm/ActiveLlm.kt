package com.example.personal_studio.core.llm

import com.example.personal_studio.BuildConfig
import com.example.personal_studio.data.local.datastore.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The endpoint compiled into this build (local.properties → BuildConfig). */
object BuiltInLlm {
    val bundled: BundledLlm
        get() = BundledLlm(BuildConfig.DEFAULT_API_KEY, BuildConfig.DEFAULT_API_BASE_URL, BuildConfig.DEFAULT_LLM_MODEL)
}

/** The endpoint requests go to right now, from the saved settings and the built-in values. */
fun UserPreferencesRepository.activeEndpoint(bundled: BundledLlm = BuiltInLlm.bundled): Flow<LlmEndpoint> =
    combine(apiFormat, apiKey, apiBaseUrl, modelName) { format, key, url, model ->
        LlmEndpoints.resolve(format, key, url, model, bundled)
    }
