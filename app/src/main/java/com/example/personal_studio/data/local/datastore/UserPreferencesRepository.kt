package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

interface UserPreferencesRepository {
    /** OpenAI-compatible API key. Null means "use bundled default from BuildConfig". */
    val apiKey: Flow<String?>
    suspend fun setApiKey(key: String?)

    /** API base URL (e.g. https://openrouter.ai/api/v1). Null means use bundled default. */
    val apiBaseUrl: Flow<String?>
    suspend fun setApiBaseUrl(url: String?)

    /** LLM model identifier (e.g. `google/gemini-2.0-flash-exp:free`). Null means default. */
    val modelName: Flow<String?>
    suspend fun setModelName(name: String?)

    /**
     * One-shot migration: if the user had a key stored under the old OpenRouter key name
     * and doesn't yet have one under [UserPreferencesKeys.API_KEY], copy it over and
     * clear the legacy entry. Safe to call multiple times.
     */
    suspend fun migrateLegacyKeysIfNeeded()
}

class UserPreferencesRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    override val apiKey: Flow<String?> =
        dataStore.data.map { it[UserPreferencesKeys.API_KEY]?.takeIf { v -> v.isNotBlank() } }

    override suspend fun setApiKey(key: String?) {
        dataStore.edit { prefs ->
            if (key.isNullOrBlank()) prefs.remove(UserPreferencesKeys.API_KEY)
            else prefs[UserPreferencesKeys.API_KEY] = key
        }
    }

    override val apiBaseUrl: Flow<String?> =
        dataStore.data.map { it[UserPreferencesKeys.API_BASE_URL]?.takeIf { v -> v.isNotBlank() } }

    override suspend fun setApiBaseUrl(url: String?) {
        dataStore.edit { prefs ->
            if (url.isNullOrBlank()) prefs.remove(UserPreferencesKeys.API_BASE_URL)
            else prefs[UserPreferencesKeys.API_BASE_URL] = url
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

    override suspend fun migrateLegacyKeysIfNeeded() {
        val snapshot = dataStore.data.firstOrNull() ?: return
        val current = snapshot[UserPreferencesKeys.API_KEY]
        val legacy = snapshot[UserPreferencesKeys.LEGACY_OPENROUTER_API_KEY]

        if (current.isNullOrBlank() && !legacy.isNullOrBlank()) {
            dataStore.edit { prefs ->
                prefs[UserPreferencesKeys.API_KEY] = legacy
                prefs.remove(UserPreferencesKeys.LEGACY_OPENROUTER_API_KEY)
            }
        } else if (!legacy.isNullOrBlank()) {
            // Already have a new key; just clean up the stale legacy entry.
            dataStore.edit { prefs -> prefs.remove(UserPreferencesKeys.LEGACY_OPENROUTER_API_KEY) }
        }
    }
}
