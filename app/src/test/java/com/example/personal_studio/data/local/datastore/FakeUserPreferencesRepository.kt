package com.example.personal_studio.data.local.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val state = MutableStateFlow<String?>(null)
    override val geminiApiKey: Flow<String?> = state
    override suspend fun setGeminiApiKey(key: String?) {
        state.value = if (key.isNullOrBlank()) null else key
    }
}
