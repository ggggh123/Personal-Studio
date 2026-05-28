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

/** M4 后台出分轮询的偏好集。Signature 集以 `\n` 分隔的 String 序列化。 */
data class GradesSyncState(
    val enabled: Boolean,
    val intervalHours: Int,
    val lastSyncAt: Long?,
    val lastSeenSignature: Set<String>,
)

@Singleton
class GradesSyncPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keyEnabled = booleanPreferencesKey("grades_poll_enabled")
    private val keyInterval = intPreferencesKey("grades_poll_interval_hours")
    private val keyLastSyncAt = longPreferencesKey("grades_last_sync_at")
    private val keyLastSig = stringPreferencesKey("grades_last_seen_signature")

    val observe: Flow<GradesSyncState> = dataStore.data.map { p ->
        GradesSyncState(
            enabled = p[keyEnabled] ?: false,
            intervalHours = p[keyInterval] ?: 6,
            lastSyncAt = p[keyLastSyncAt],
            lastSeenSignature = p[keyLastSig]?.split('\n')?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
        )
    }

    /** 一次性快照,Worker / BootReceiver 用。 */
    suspend fun snapshot(): GradesSyncState = observe.first()

    suspend fun setEnabled(v: Boolean) = dataStore.edit { it[keyEnabled] = v }
    suspend fun setIntervalHours(v: Int) = dataStore.edit { it[keyInterval] = v }
    suspend fun setLastSyncAt(v: Long) = dataStore.edit { it[keyLastSyncAt] = v }
    suspend fun setLastSeenSignature(sigs: Set<String>) = dataStore.edit {
        it[keyLastSig] = sigs.joinToString("\n")
    }
}
