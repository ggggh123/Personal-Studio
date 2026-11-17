package com.example.personal_studio.domain.emptyroom

import com.example.personal_studio.data.local.datastore.TimetablePreferences
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.dto.RoomOccupancyDto
import com.example.personal_studio.domain.bitimport.SsoLoginUseCase
import com.example.personal_studio.domain.emptyroom.model.Building
import com.example.personal_studio.domain.emptyroom.model.Campus
import com.example.personal_studio.domain.emptyroom.model.EmptyRoomCatalog
import com.example.personal_studio.domain.emptyroom.model.EmptyRoomError
import com.example.personal_studio.domain.emptyroom.model.RoomFreeSlots
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** 一次会话的结果(成功)或错误。 */
sealed interface EmptyRoomResult<out T> {
    data class Ok<T>(val value: T) : EmptyRoomResult<T>
    data class Err(val error: EmptyRoomError) : EmptyRoomResult<Nothing>
}

@Singleton
class EmptyRoomRepository @Inject constructor(
    private val apiClient: BitApiClient,
    private val ssoLogin: SsoLoginUseCase,
    private val compute: ComputeFreeSlotsUseCase,
    private val timetablePrefs: TimetablePreferences,
) {
    // (楼code|日期) → 原始占用行缓存(时间无关;status 每次按 live nowMinute 重算)。
    private val occupancyCache = mutableMapOf<String, List<RoomOccupancyDto>>()

    /** 打开会话并登录;成功返回当前学期串("2025-2026-2"),失败返回 Err。调用方负责在用完后 close()。 */
    suspend fun openAndLogin(username: String, password: String, mode: NetworkMode): EmptyRoomResult<String> {
        apiClient.open(mode)
        val login = ssoLogin.invoke(apiClient, username, password)
        login.toError()?.let { return EmptyRoomResult.Err(it) }
        apiClient.jwapp.getAppConfig()   // 空教室复用 wdkbby 预热,见 docs/school-integration.md。
        apiClient.jwapp.switchLang()
        val term = apiClient.jwapp.getCurrentTerm().body()?.datas?.dqxnxq?.rows?.firstOrNull()?.code
            ?: return EmptyRoomResult.Err(EmptyRoomError.Parse("无当前学期"))
        return EmptyRoomResult.Ok(term)
    }

    suspend fun acquireSession() = apiClient.acquireSession()

    fun close() {
        try { apiClient.close() } finally { apiClient.releaseSession() }
    }

    /**
     * Campuses and every building, inside an open session. The building list without a campus
     * filter carries each building's campus code, so one request covers all campuses; if a row
     * lacks it, the buildings are fetched campus by campus in the same session instead.
     */
    suspend fun fetchCatalog(now: Long): EmptyRoomCatalog {
        val campuses = apiClient.jwapp.getCampuses().body()?.datas?.ggzdpx?.rows.orEmpty()
            .mapNotNull { d -> d.code?.let { Campus(it, d.name.orEmpty()) } }
        val all = apiClient.jwapp.getBuildings(null).body()?.datas?.cxjxl?.rows.orEmpty()
        val buildings = if (all.isNotEmpty() && all.all { !it.campusCode.isNullOrBlank() && it.code != null }) {
            all.map { Building(it.code!!, it.name.orEmpty(), it.campusCode!!) }
        } else {
            campuses.flatMap { campus ->
                apiClient.jwapp.getBuildings(campus.code).body()?.datas?.cxjxl?.rows.orEmpty()
                    .mapNotNull { d -> d.code?.let { Building(it, d.name.orEmpty(), campus.code) } }
            }
        }
        return EmptyRoomCatalog(campuses, buildings.distinctBy { it.code }, now)
    }

    /** The rooms of [targets] on [date] from memory, or null when any building still has to be fetched. */
    suspend fun cachedOccupancy(targets: List<Building>, date: String, nowMinute: Int): List<RoomFreeSlots>? {
        if (targets.any { "${it.code}|$date" !in occupancyCache }) return null
        val clock = PeriodClock(timetablePrefs.periods.first())
        return targets.flatMap { b ->
            occupancyCache.getValue("${b.code}|$date").map { compute.invoke(it, b.code, b.name, clock, nowMinute) }
        }
    }

    /** Forget what was fetched for these buildings on [date] (pull to refresh). */
    fun dropOccupancy(targets: List<Building>, date: String) {
        targets.forEach { occupancyCache.remove("${it.code}|$date") }
    }

    /** 拉某楼某日占用并算空闲。nowMinute = 用于状态计算的当天分钟数(每次重算,不缓存进结果)。 */
    suspend fun occupancy(building: Building, date: String, term: String, nowMinute: Int): List<RoomFreeSlots> {
        val cacheKey = "${building.code}|$date"
        val clock = PeriodClock(timetablePrefs.periods.first())
        val rawRows = occupancyCache[cacheKey] ?: run {
            val (xnxqdm, xndm, xqdm) = deriveTerm(term)
            val resp = apiClient.jwapp.getRoomOccupancy(
                xqdm = xqdm, jxldm = building.code, rq = date, xnxqdm = xnxqdm, xndm = xndm,
            )
            (resp.body()?.datas?.cxkxjasqk?.rows.orEmpty()).also { occupancyCache[cacheKey] = it }
        }
        return rawRows.map { compute.invoke(it, building.code, building.name, clock, nowMinute) }
    }

    /** 并发拉给定各楼的占用,合并(支持跨校区多选)。 */
    suspend fun occupancyForBuildings(targets: List<Building>, date: String, term: String, nowMinute: Int): List<RoomFreeSlots> =
        coroutineScope {
            targets.map { b -> async { occupancy(b, date, term, nowMinute) } }.map { it.await() }.flatten()
        }

    /** 把学期串 "2025-2026-2" 拆成 (XNXQDM, XNDM, XQDM)。 */
    private fun deriveTerm(term: String): Triple<String, String, String> =
        Triple(term, term.substringBeforeLast('-'), term.substringAfterLast('-'))

    private fun CasLoginDto.toError(): EmptyRoomError? = when (this) {
        CasLoginDto.Success -> null
        CasLoginDto.WrongCredentials -> EmptyRoomError.WrongCredentials
        CasLoginDto.AccountLocked -> EmptyRoomError.WrongCredentials
        CasLoginDto.CaptchaRequired -> EmptyRoomError.Parse("请到校园页点“重新验证”，并完成应用内的学校网页验证")
        is CasLoginDto.UnknownFailure -> EmptyRoomError.Parse("CAS: $body")
    }
}
