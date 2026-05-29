package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 首屏登录门:是否已见过登录页(跳过或登录成功都置 true,首屏门只出现一次)。 */
@Singleton
class LoginPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keySeen = booleanPreferencesKey("login_has_seen")

    val observe: Flow<Boolean> = dataStore.data.map { it[keySeen] ?: false }
    suspend fun snapshot(): Boolean = observe.first()
    suspend fun setHasSeenLogin(v: Boolean) = dataStore.edit { it[keySeen] = v }
}
