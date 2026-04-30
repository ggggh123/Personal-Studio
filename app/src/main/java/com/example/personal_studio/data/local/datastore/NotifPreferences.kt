package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class NotifSwitches(
    val course: Boolean,
    val task: Boolean,
    val custom: Boolean,
    val bannerDismissedThisSession: Boolean,
)

@Singleton
class NotifPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keyCourse = booleanPreferencesKey("notif_course_enabled")
    private val keyTask = booleanPreferencesKey("notif_task_enabled")
    private val keyCustom = booleanPreferencesKey("notif_custom_enabled")
    private val keyBannerDismissed = booleanPreferencesKey("notif_banner_dismissed_session")

    val switches: Flow<NotifSwitches> = dataStore.data.map { prefs ->
        NotifSwitches(
            course = prefs[keyCourse] ?: true,
            task = prefs[keyTask] ?: true,
            custom = prefs[keyCustom] ?: true,
            bannerDismissedThisSession = prefs[keyBannerDismissed] ?: false,
        )
    }

    suspend fun setCourse(enabled: Boolean) = dataStore.edit { it[keyCourse] = enabled }
    suspend fun setTask(enabled: Boolean) = dataStore.edit { it[keyTask] = enabled }
    suspend fun setCustom(enabled: Boolean) = dataStore.edit { it[keyCustom] = enabled }
    suspend fun setBannerDismissed(dismissed: Boolean) =
        dataStore.edit { it[keyBannerDismissed] = dismissed }
}
