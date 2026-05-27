package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 上次 AI 学情报告的本地缓存。打开成绩单时若有缓存就直接显示,点「重新生成」才重跑。 */
data class CachedAnalysis(val text: String, val at: Long)

@Singleton
class GradesAnalysisCache @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keyText = stringPreferencesKey("grades_ai_text")
    private val keyAt = longPreferencesKey("grades_ai_at")

    val observe: Flow<CachedAnalysis?> = dataStore.data.map { prefs ->
        val text = prefs[keyText]
        val at = prefs[keyAt]
        if (text.isNullOrBlank() || at == null) null else CachedAnalysis(text, at)
    }

    suspend fun save(text: String) = dataStore.edit {
        it[keyText] = text
        it[keyAt] = System.currentTimeMillis()
    }

    suspend fun clear() = dataStore.edit {
        it.remove(keyText)
        it.remove(keyAt)
    }
}
