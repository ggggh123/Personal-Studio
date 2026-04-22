package com.example.personal_studio.data.local.datastore

import androidx.datastore.preferences.core.stringPreferencesKey

internal object UserPreferencesKeys {
    val API_KEY = stringPreferencesKey("api_key")
    val API_BASE_URL = stringPreferencesKey("api_base_url")
    val LLM_MODEL = stringPreferencesKey("llm_model")

    // Legacy keys — migrated to API_KEY on first launch, then ignored.
    val LEGACY_OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
}
