package com.example.personal_studio.data.local.datastore

import androidx.datastore.preferences.core.stringPreferencesKey

internal object UserPreferencesKeys {
    val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
    val LLM_MODEL = stringPreferencesKey("llm_model")
}
