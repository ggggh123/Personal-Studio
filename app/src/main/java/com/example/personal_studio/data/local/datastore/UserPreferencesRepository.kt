package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UserPreferencesRepository {
    /** OpenRouter API key. Null means "use bundled default from BuildConfig". */
    val openRouterApiKey: Flow<String?>
    suspend fun setOpenRouterApiKey(key: String?)

    /** LLM model identifier (e.g. `google/gemini-2.0-flash-exp:free`). Null means use default. */
    val modelName: Flow<String?>
    suspend fun setModelName(name: String?)
}

class UserPreferencesRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    override val openRouterApiKey: Flow<String?> =
        dataStore.data.map { it[UserPreferencesKeys.OPENROUTER_API_KEY]?.takeIf { v -> v.isNotBlank() } }

    override suspend fun setOpenRouterApiKey(key: String?) {
        dataStore.edit { prefs ->
            if (key.isNullOrBlank()) prefs.remove(UserPreferencesKeys.OPENROUTER_API_KEY)
            else prefs[UserPreferencesKeys.OPENROUTER_API_KEY] = key
        }
    }

    override val modelName: Flow<String?> =
        dataStore.data.map { it[UserPreferencesKeys.LLM_MODEL]?.takeIf { v -> v.isNotBlank() } }

    override suspend fun setModelName(name: String?) {
        dataStore.edit { prefs ->
            if (name.isNullOrBlank()) prefs.remove(UserPreferencesKeys.LLM_MODEL)
            else prefs[UserPreferencesKeys.LLM_MODEL] = name
        }
    }
}
