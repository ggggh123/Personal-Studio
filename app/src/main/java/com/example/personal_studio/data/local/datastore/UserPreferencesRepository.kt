package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UserPreferencesRepository {
    val geminiApiKey: Flow<String?>
    suspend fun setGeminiApiKey(key: String?)
}

class UserPreferencesRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    override val geminiApiKey: Flow<String?> =
        dataStore.data.map { it[UserPreferencesKeys.GEMINI_API_KEY]?.takeIf { v -> v.isNotBlank() } }

    override suspend fun setGeminiApiKey(key: String?) {
        dataStore.edit { prefs ->
            if (key.isNullOrBlank()) prefs.remove(UserPreferencesKeys.GEMINI_API_KEY)
            else prefs[UserPreferencesKeys.GEMINI_API_KEY] = key
        }
    }
}
