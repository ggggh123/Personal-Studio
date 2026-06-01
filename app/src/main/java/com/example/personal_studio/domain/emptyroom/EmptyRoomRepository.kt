package com.example.personal_studio.domain.emptyroom

import com.example.personal_studio.data.local.datastore.TimetablePreferences
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.domain.bitimport.SsoLoginUseCase
import com.example.personal_studio.domain.emptyroom.model.Building
import com.example.personal_studio.domain.emptyroom.model.Campus
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
    // (楼code|日期) → 占用结果缓存(当天稳定)。
    private val occupancyCache = mutableMapOf<String, List<RoomFreeSlots>>()
    private var campusesCache: List<Campus>? = null
    private val buildingsCache = mutableMapOf<String, List<Building>>() // campusCode("" =all) → buildings

    /** 打开会话并登录;成功返回当前学期串("2025-2026-2"),失败返回 Err。调用方负责在用完后 close()。 */
    suspend fun openAndLogin(username: String, password: String, mode: NetworkMode): EmptyRoomResult<String> {
        apiClient.open(mode)
        val login = ssoLogin.invoke(apiClient, username, password)
        login.toError()?.let { return EmptyRoomResult.Err(it) }
        apiClient.jwapp.getAppConfig()   // wdkbby warm-up(空教室复用,见 spec)
        apiClient.jwapp.switchLang()
        val term = apiClient.jwapp.getCurrentTerm().body()?.datas?.dqxnxq?.rows?.firstOrNull()?.code
            ?: return EmptyRoomResult.Err(EmptyRoomError.Parse("无当前学期"))
        return EmptyRoomResult.Ok(term)
    }

    fun close() = apiClient.close()

    suspend fun campuses(): List<Campus> {
        campusesCache?.let { return it }
        val rows = apiClient.jwapp.getCampuses().body()?.datas?.ggzdpx?.rows.orEmpty()
        return rows.mapNotNull { d -> d.code?.let { Campus(it, d.name.orEmpty()) } }.also { campusesCache = it }
    }

    suspend fun buildings(campusCode: String?): List<Building> {
        val key = campusCode.orEmpty()
        buildingsCache[key]?.let { return it }
        val rows = apiClient.jwapp.getBuildings(campusCode).body()?.datas?.cxjxl?.rows.orEmpty()
        return rows.mapNotNull { d -> d.code?.let { Building(it, d.name.orEmpty(), d.campusCode.orEmpty()) } }
            .also { buildingsCache[key] = it }
    }

    /** 拉某楼某日占用并算空闲。nowMinute = 用于状态计算的当天分钟数。 */
    suspend fun occupancy(building: Building, date: String, term: String, nowMinute: Int): List<RoomFreeSlots> {
        val cacheKey = "${building.code}|$date"
        occupancyCache[cacheKey]?.let { return it }
        val clock = PeriodClock(timetablePrefs.periods.first())
        val (xnxqdm, xndm, xqdm) = deriveTerm(term)
        val resp = apiClient.jwapp.getRoomOccupancy(
            xqdm = xqdm, jxldm = building.code, rq = date, xnxqdm = xnxqdm, xndm = xndm,
        )
        val rows = resp.body()?.datas?.cxkxjasqk?.rows.orEmpty()
        val result = rows.map { compute.invoke(it, building.name, clock, nowMinute) }
        occupancyCache[cacheKey] = result
        return result
    }

    /** 全校区:并发拉该校区所有楼,合并。 */
    suspend fun occupancyForCampus(campusCode: String, date: String, term: String, nowMinute: Int): List<RoomFreeSlots> =
        coroutineScope {
            buildings(campusCode).map { b -> async { occupancy(b, date, term, nowMinute) } }.map { it.await() }.flatten()
        }

    fun clearDayCache() { occupancyCache.clear() }

    /** 把学期串 "2025-2026-2" 拆成 (XNXQDM, XNDM, XQDM)。 */
    private fun deriveTerm(term: String): Triple<String, String, String> =
        Triple(term, term.substringBeforeLast('-'), term.substringAfterLast('-'))

    private fun CasLoginDto.toError(): EmptyRoomError? = when (this) {
        CasLoginDto.Success -> null
        CasLoginDto.WrongCredentials -> EmptyRoomError.WrongCredentials
        CasLoginDto.AccountLocked -> EmptyRoomError.WrongCredentials
        CasLoginDto.CaptchaRequired -> EmptyRoomError.Parse("需验证码,请网页端登录一次")
        is CasLoginDto.UnknownFailure -> EmptyRoomError.Parse("CAS: $body")
    }
}
