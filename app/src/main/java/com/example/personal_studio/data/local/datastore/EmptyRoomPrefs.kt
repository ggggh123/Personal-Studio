package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.personal_studio.domain.emptyroom.model.EmptyRoomCatalog
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** What 空教室 remembers between visits: the place and time filter last used, and recent buildings. */
data class EmptyRoomSelection(
    val campusCode: String? = null,
    /** Empty = every building of the campus. */
    val buildingCodes: Set<String> = emptySet(),
    val timeFilter: String? = null,
    val customFrom: Int = 1,
    val customTo: Int = 2,
    /** Most recent first, at most [EmptyRoomPrefs.RECENT_MAX]. */
    val recentBuildingCodes: List<String> = emptyList(),
)

/** The campus / building catalog and the last selection for 空教室, in the shared preferences store. */
@Singleton
class EmptyRoomPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keyCatalog = stringPreferencesKey("empty_room_catalog")
    private val keyCampus = stringPreferencesKey("empty_room_campus")
    private val keyBuildings = stringPreferencesKey("empty_room_buildings")
    private val keyFilter = stringPreferencesKey("empty_room_filter")
    private val keyCustom = stringPreferencesKey("empty_room_custom")
    private val keyRecent = stringPreferencesKey("empty_room_recent")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun catalog(): EmptyRoomCatalog? =
        dataStore.data.first()[keyCatalog]?.let { runCatching { json.decodeFromString<EmptyRoomCatalog>(it) }.getOrNull() }

    suspend fun saveCatalog(catalog: EmptyRoomCatalog) {
        dataStore.edit { it[keyCatalog] = json.encodeToString(EmptyRoomCatalog.serializer(), catalog) }
    }

    suspend fun selection(): EmptyRoomSelection {
        val p = dataStore.data.first()
        val custom = p[keyCustom]?.split('-')?.mapNotNull { it.toIntOrNull() }.orEmpty()
        return EmptyRoomSelection(
            campusCode = p[keyCampus]?.takeIf { it.isNotEmpty() },
            buildingCodes = p[keyBuildings].toCodes().toSet(),
            timeFilter = p[keyFilter],
            customFrom = custom.getOrNull(0) ?: 1,
            customTo = custom.getOrNull(1) ?: 2,
            recentBuildingCodes = p[keyRecent].toCodes(),
        )
    }

    suspend fun saveSelection(s: EmptyRoomSelection) {
        dataStore.edit {
            it[keyCampus] = s.campusCode.orEmpty()
            it[keyBuildings] = s.buildingCodes.joinToString(",")
            s.timeFilter?.let { f -> it[keyFilter] = f }
            it[keyCustom] = "${s.customFrom}-${s.customTo}"
            it[keyRecent] = s.recentBuildingCodes.take(RECENT_MAX).joinToString(",")
        }
    }

    private fun String?.toCodes(): List<String> = this?.split(',')?.filter { it.isNotBlank() }.orEmpty()

    companion object {
        const val RECENT_MAX = 6
    }
}
