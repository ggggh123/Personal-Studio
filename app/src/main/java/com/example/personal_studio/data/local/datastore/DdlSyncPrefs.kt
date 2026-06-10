package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** P7 后台作业 DDL 轮询偏好。lastSeenUids 以 `\n` 分隔序列化;icalUrl 为策略 Y 持久化订阅 URL。 */
data class DdlSyncState(
    val enabled: Boolean,
    val intervalHours: Int,
    val lastSyncAt: Long?,
    val lastSeenUids: Set<String>,
    val icalUrl: String?,
    val lastResult: PollResult? = null,
)

@Singleton
class DdlSyncPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keyEnabled = booleanPreferencesKey("ddl_poll_enabled")
    private val keyInterval = intPreferencesKey("ddl_poll_interval_hours")
    private val keyLastSyncAt = longPreferencesKey("ddl_last_sync_at")
    private val keyLastUids = stringPreferencesKey("ddl_last_seen_uids")
    private val keyIcalUrl = stringPreferencesKey("ddl_ical_url")
    private val keyResultAt = longPreferencesKey("ddl_poll_result_at")
    private val keyResultOk = booleanPreferencesKey("ddl_poll_result_ok")
    private val keyResultTotal = intPreferencesKey("ddl_poll_result_total")
    private val keyResultNew = intPreferencesKey("ddl_poll_result_new")
    private val keyResultMsg = stringPreferencesKey("ddl_poll_result_msg")

    val observe: Flow<DdlSyncState> = dataStore.data.map { p ->
        DdlSyncState(
            enabled = p[keyEnabled] ?: false,
            intervalHours = p[keyInterval] ?: 12,
            lastSyncAt = p[keyLastSyncAt],
            lastSeenUids = p[keyLastUids]?.split('\n')?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
            icalUrl = p[keyIcalUrl],
            lastResult = p[keyResultAt]?.let { at ->
                PollResult(
                    at = at,
                    ok = p[keyResultOk] ?: false,
                    total = p[keyResultTotal] ?: 0,
                    newCount = p[keyResultNew] ?: 0,
                    message = p[keyResultMsg] ?: "",
                )
            },
        )
    }

    suspend fun snapshot(): DdlSyncState = observe.first()

    suspend fun setEnabled(v: Boolean) = dataStore.edit { it[keyEnabled] = v }
    suspend fun setIntervalHours(v: Int) = dataStore.edit { it[keyInterval] = v }
    suspend fun setLastSyncAt(v: Long) = dataStore.edit { it[keyLastSyncAt] = v }
    suspend fun setLastSeenUids(uids: Set<String>) = dataStore.edit {
        it[keyLastUids] = uids.joinToString("\n")
    }
    suspend fun setIcalUrl(url: String) = dataStore.edit { it[keyIcalUrl] = url }
    suspend fun clearIcalUrl() = dataStore.edit { it.remove(keyIcalUrl) }
    suspend fun setLastResult(r: PollResult) = dataStore.edit {
        it[keyResultAt] = r.at
        it[keyResultOk] = r.ok
        it[keyResultTotal] = r.total
        it[keyResultNew] = r.newCount
        it[keyResultMsg] = r.message
    }
}
