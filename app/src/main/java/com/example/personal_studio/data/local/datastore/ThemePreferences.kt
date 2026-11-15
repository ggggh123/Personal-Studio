package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.personal_studio.ui.theme.daylight.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Appearance preferences: the theme mode, plus a debug-only "fixed moment" so the five
 * reference moments of the 天光 theme can be checked on a device at any time of day.
 */
@Singleton
class ThemePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keyMode = stringPreferencesKey("theme_mode")
    private val keyDebugMoment = stringPreferencesKey("debug_theme_moment")

    val mode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[keyMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DAYLIGHT
    }

    suspend fun setMode(mode: ThemeMode) {
        dataStore.edit { it[keyMode] = mode.name }
    }

    /** Debug builds only: null follows the real clock. */
    val debugMoment: Flow<DebugMoment?> = dataStore.data.map { prefs ->
        prefs[keyDebugMoment]?.let { runCatching { DebugMoment.valueOf(it) }.getOrNull() }
    }

    suspend fun setDebugMoment(moment: DebugMoment?) {
        dataStore.edit {
            if (moment == null) it.remove(keyDebugMoment) else it[keyDebugMoment] = moment.name
        }
    }
}

/** The design's five reference moments, expressed relative to today's sun. */
enum class DebugMoment(val label: String) {
    DAWN("拂晓"), MORNING("上午"), NOON("正午"), SUNSET("日落"), NIGHT("深夜"),
}
