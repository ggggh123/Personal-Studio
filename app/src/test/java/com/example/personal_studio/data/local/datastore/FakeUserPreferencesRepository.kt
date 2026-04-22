package com.example.personal_studio.data.local.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val keyState = MutableStateFlow<String?>(null)
    private val baseUrlState = MutableStateFlow<String?>(null)
    private val modelState = MutableStateFlow<String?>(null)

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

    override suspend fun migrateLegacyKeysIfNeeded() {
        // no-op for tests
    }
}
