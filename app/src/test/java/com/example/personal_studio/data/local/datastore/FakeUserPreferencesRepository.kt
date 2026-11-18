package com.example.personal_studio.data.local.datastore

import com.example.personal_studio.core.llm.GenerationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val keyState = MutableStateFlow<String?>(null)
    private val baseUrlState = MutableStateFlow<String?>(null)
    private val modelState = MutableStateFlow<String?>(null)
    private val formatState = MutableStateFlow<String?>(null)
    private val generationState = MutableStateFlow(GenerationSettings())

    override val apiKey: Flow<String?> = keyState
    override suspend fun setApiKey(key: String?) {
        keyState.value = if (key.isNullOrBlank()) null else key
    }

    override val apiBaseUrl: Flow<String?> = baseUrlState
    override suspend fun setApiBaseUrl(url: String?) {
        baseUrlState.value = if (url.isNullOrBlank()) null else url
    }

    override val modelName: Flow<String?> = modelState
    override suspend fun setModelName(name: String?) {
        modelState.value = if (name.isNullOrBlank()) null else name
    }

    override val apiFormat: Flow<String?> = formatState
    override suspend fun setApiFormat(format: String?) {
        formatState.value = if (format.isNullOrBlank()) null else format
    }

    override val generation: Flow<GenerationSettings> = generationState
    override suspend fun setGeneration(settings: GenerationSettings) {
        generationState.value = settings
    }

    override suspend fun migrateLegacyKeysIfNeeded() {
        // no-op for tests
    }
}
