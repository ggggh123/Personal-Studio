package com.example.personal_studio.data.local.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val keyState = MutableStateFlow<String?>(null)
    private val modelState = MutableStateFlow<String?>(null)

    override val openRouterApiKey: Flow<String?> = keyState
    override suspend fun setOpenRouterApiKey(key: String?) {
        keyState.value = if (key.isNullOrBlank()) null else key
    }

    override val modelName: Flow<String?> = modelState
    override suspend fun setModelName(name: String?) {
        modelState.value = if (name.isNullOrBlank()) null else name
    }
}
