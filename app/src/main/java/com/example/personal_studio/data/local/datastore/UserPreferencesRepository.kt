package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.personal_studio.core.llm.GenerationSettings
import com.example.personal_studio.core.llm.ReasoningLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface UserPreferencesRepository {
    /** API key of the custom endpoint. Null means "use bundled default from BuildConfig". */
    val apiKey: Flow<String?>
    suspend fun setApiKey(key: String?)

    /** API base URL (e.g. https://openrouter.ai/api/v1). Null means use bundled default. */
    val apiBaseUrl: Flow<String?>
    suspend fun setApiBaseUrl(url: String?)

    /** LLM model identifier (e.g. `google/gemini-2.0-flash-exp:free`). Null means default. */
    val modelName: Flow<String?>
    suspend fun setModelName(name: String?)

    /** `ApiFormat.id` of the custom endpoint; null means OpenAI-compatible. */
    val apiFormat: Flow<String?> get() = flowOf(null)
    suspend fun setApiFormat(format: String?) {}

    /** Reasoning level, temperature, output length and top-p for every request. */
    val generation: Flow<GenerationSettings> get() = flowOf(GenerationSettings())
    suspend fun setGeneration(settings: GenerationSettings) {}

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

    override val apiFormat: Flow<String?> =
        dataStore.data.map { it[UserPreferencesKeys.API_FORMAT]?.takeIf { v -> v.isNotBlank() } }

    override suspend fun setApiFormat(format: String?) {
        dataStore.edit { prefs ->
            if (format.isNullOrBlank()) prefs.remove(UserPreferencesKeys.API_FORMAT)
            else prefs[UserPreferencesKeys.API_FORMAT] = format
        }
    }

    override val generation: Flow<GenerationSettings> = dataStore.data.map { p ->
        GenerationSettings(
            reasoning = ReasoningLevel.of(p[UserPreferencesKeys.REASONING]),
            temperature = p[UserPreferencesKeys.TEMPERATURE],
            maxTokens = p[UserPreferencesKeys.MAX_TOKENS],
            topP = p[UserPreferencesKeys.TOP_P],
        )
    }

    override suspend fun setGeneration(settings: GenerationSettings) {
        dataStore.edit { prefs ->
            if (settings.reasoning == ReasoningLevel.AUTO) prefs.remove(UserPreferencesKeys.REASONING)
            else prefs[UserPreferencesKeys.REASONING] = settings.reasoning.id
            settings.temperature?.let { prefs[UserPreferencesKeys.TEMPERATURE] = it } ?: prefs.remove(UserPreferencesKeys.TEMPERATURE)
            settings.maxTokens?.let { prefs[UserPreferencesKeys.MAX_TOKENS] = it } ?: prefs.remove(UserPreferencesKeys.MAX_TOKENS)
            settings.topP?.let { prefs[UserPreferencesKeys.TOP_P] = it } ?: prefs.remove(UserPreferencesKeys.TOP_P)
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
