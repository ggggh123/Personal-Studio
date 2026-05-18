package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SemesterPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keyStartDate = stringPreferencesKey("semester_start_date")

    /** Null when never set (triggers SemesterStartModal on first AddCourse). */
    val startDate: Flow<LocalDate?> = dataStore.data.map { prefs ->
        prefs[keyStartDate]?.let(LocalDate::parse)
    }

    suspend fun setStartDate(date: LocalDate) {
        dataStore.edit { it[keyStartDate] = date.toString() }
    }
}
