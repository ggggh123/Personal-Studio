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

/** 首屏登录门:是否已见过登录页;以及首次批量同步是否已完成(只自动跑一次)。 */
@Singleton
class LoginPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keySeen = booleanPreferencesKey("login_has_seen")
    private val keyFirstSyncDone = booleanPreferencesKey("first_sync_done")

    val observe: Flow<Boolean> = dataStore.data.map { it[keySeen] ?: false }
    suspend fun snapshot(): Boolean = observe.first()
    suspend fun setHasSeenLogin(v: Boolean) = dataStore.edit { it[keySeen] = v }

    val observeFirstSyncDone: Flow<Boolean> = dataStore.data.map { it[keyFirstSyncDone] ?: false }
    suspend fun snapshotFirstSyncDone(): Boolean = observeFirstSyncDone.first()
    suspend fun setFirstSyncDone(v: Boolean) = dataStore.edit { it[keyFirstSyncDone] = v }
}
