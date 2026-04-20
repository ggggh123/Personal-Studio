package com.example.personal_studio.data.local.datastore

import androidx.datastore.preferences.core.stringPreferencesKey

internal object UserPreferencesKeys {
    val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
}
