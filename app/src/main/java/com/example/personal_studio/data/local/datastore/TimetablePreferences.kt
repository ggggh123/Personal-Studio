package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.core.util.TimetablePeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimetablePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keyJson = stringPreferencesKey("timetable_periods_json")

    val periods: Flow<List<TimetablePeriod>> = dataStore.data.map { prefs ->
        val raw = prefs[keyJson]
        if (raw.isNullOrBlank()) DefaultTimetable.PERIODS
        else runCatching { Json.decodeFromString<List<TimetablePeriod>>(raw) }
            .getOrDefault(DefaultTimetable.PERIODS)
    }

    suspend fun setPeriods(periods: List<TimetablePeriod>) {
        dataStore.edit { it[keyJson] = Json.encodeToString(periods) }
    }

    suspend fun resetToDefault() {
        dataStore.edit { it.remove(keyJson) }
    }
}
