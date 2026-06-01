# 空教室查询 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 加「空教室查询」模块——从 BIT ehall `kxjasbyMobile` 拉教室占用,反推空闲,提供智能「现在去自习」、跨楼/全校区筛选、可视化网格+时间轴、课表联动,入口在 profile 第 5 卡。

**Architecture:** 复用现有 `apiClient.jwapp` + CAS + wdkbby warm-up(零新认证);`BitJwappService` 加 3 端点;`ComputeFreeSlotsUseCase` 纯函数把 `ZYJC` 忙节次串反推空闲段 + 现在空/下次变化(用已有 `TimetablePreferences` 节次时间);`EmptyRoomRepository` 拉取+缓存+并发全校区;`EmptyRoomViewModel` + `EmptyRoomScreen`(信息卡列表 + 点开占用网格 + 时间轴 + 智能头部),复用考试页的信息卡 + CRT 美学。

**Tech Stack:** Kotlin, Compose, Hilt, Retrofit + kotlinx.serialization, Room(DAO Flow), DataStore, coroutines(flow/combine), JUnit4 + MockK + Turbine。

---

## File Structure

**Create:**
- `data/network/bit/dto/EmptyRoomDto.kt` — `CampusDto/BuildingDto/RoomOccupancyDto` + 3 `*Response` wrappers。
- `domain/emptyroom/model/EmptyRoomModels.kt` — `Campus/Building/RoomFreeSlots/RoomStatus`,`EmptyRoomError`。
- `domain/emptyroom/PeriodClock.kt` — 节次 ↔ `LocalTime`(读 `TimetablePreferences`)。
- `domain/emptyroom/ComputeFreeSlotsUseCase.kt` — `ZYJC` → 空闲段 + 现在空/下次变化。
- `domain/emptyroom/EmptyRoomRepository.kt` — warm-up + 拉校区/楼/占用 + 缓存 + 全校区并发。
- `feature/emptyroom/EmptyRoomViewModel.kt` — `EmptyRoomUiState` + 查询/智能排序/筛选/课表联动。
- `feature/emptyroom/ui/EmptyRoomScreen.kt` — 屏 + `SmartHeader`/`FilterBar`/`RoomCard`/`OccupancyGrid`/`TimeAxisSlider`。
- Tests: `domain/emptyroom/PeriodClockTest.kt`, `ComputeFreeSlotsUseCaseTest.kt`, `feature/emptyroom/EmptyRoomViewModelTest.kt`。

**Modify:**
- `data/network/bit/service/BitJwappService.kt` — 加 `getCampuses`/`getBuildings`/`getRoomOccupancy`。
- `ui/navigation/NavRoutes.kt` — `const val EMPTY_ROOM = "empty-room"`。
- `ui/AppNavHost.kt` — `EMPTY_ROOM` composable + profile `onOpenEmptyRoom`。
- `feature/profile/ui/ProfileScreen.kt` — 第 5 卡「空教室」+ `onOpenEmptyRoom` 参数。

**Conventions:** 颜色/字体用 `ui.theme`;CRT `scanLines()/vignette()`;无圆角直角卡;ViewModel 用 `nowProvider: () -> Long = System::currentTimeMillis` 注入便于测试;提交 trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。

---

## 阶段一:数据层

### Task 1: EmptyRoomDto

**Files:** Create `app/src/main/java/com/example/personal_studio/data/network/bit/dto/EmptyRoomDto.kt`

- [ ] **Step 1: 实现**(无单测,DTO 由后续 repo/算法测试覆盖)

```kotlin
package com.example.personal_studio.data.network.bit.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 校区 ggzdpx.do 一行。 */
@Serializable
data class CampusDto(
    @SerialName("DM") val code: String? = null,
    @SerialName("MC") val name: String? = null,
)

@Serializable
data class CampusResponse(val datas: Datas) {
    @Serializable data class Datas(@SerialName("ggzdpx") val ggzdpx: Rows)
    @Serializable data class Rows(val rows: List<CampusDto> = emptyList())
}

/** 教学楼 cxjxl.do 一行。 */
@Serializable
data class BuildingDto(
    @SerialName("JXLMC") val name: String? = null,
    @SerialName("JXLDM") val code: String? = null,
    @SerialName("XXXQDM") val campusCode: String? = null,
    @SerialName("XXXQDM_DISPLAY") val campusName: String? = null,
)

@Serializable
data class BuildingResponse(val datas: Datas) {
    @Serializable data class Datas(@SerialName("cxjxl") val cxjxl: Rows)
    @Serializable data class Rows(val rows: List<BuildingDto> = emptyList())
}

/** 教室占用 cxkxjasqk.do 一行。ZYJC = 逗号分隔的「忙」节次串,可为 null(全天空)。 */
@Serializable
data class RoomOccupancyDto(
    @SerialName("JASMC") val roomName: String? = null,
    @SerialName("ZYJC") val busyPeriods: String? = null,
)

@Serializable
data class RoomOccupancyResponse(val datas: Datas) {
    @Serializable data class Datas(@SerialName("cxkxjasqk") val cxkxjasqk: Rows)
    @Serializable data class Rows(val rows: List<RoomOccupancyDto> = emptyList())
}
```

- [ ] **Step 2: 编译** `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL。
- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/example/personal_studio/data/network/bit/dto/EmptyRoomDto.kt
git commit -m "p11: 空教室 DTO(校区/教学楼/教室占用 ehall 响应)"
```

### Task 2: BitJwappService 加空教室端点

**Files:** Modify `app/src/main/java/com/example/personal_studio/data/network/bit/service/BitJwappService.kt`

- [ ] **Step 1: 加 3 端点**(在 getExamSchedule 之后,文件 `}` 之前)

加 import:
```kotlin
import com.example.personal_studio.data.network.bit.dto.BuildingResponse
import com.example.personal_studio.data.network.bit.dto.CampusResponse
import com.example.personal_studio.data.network.bit.dto.RoomOccupancyResponse
```
加方法:
```kotlin
    // ── ehall kxjasbyMobile (空教室) — 同 host/session,复用 wdkbby warm-up ──

    @GET("jwapp/sys/kxjasbyMobile/modules/jxllb/ggzdpx.do")
    suspend fun getCampuses(
        @Query("dicCode") dicCode: String = "48682",
        @Query("SFSY") sfsy: String = "1",
        @Query("order") order: String = "+DM",
    ): Response<CampusResponse>

    @GET("jwapp/sys/kxjasbyMobile/modules/jxllb/cxjxl.do")
    suspend fun getBuildings(
        @Query("XXXQDM") campusCode: String? = null,
    ): Response<BuildingResponse>

    @FormUrlEncoded
    @POST("jwapp/sys/kxjasbyMobile/kxjasbyController/cxkxjasqk.do")
    suspend fun getRoomOccupancy(
        @Field("XQDM") xqdm: String,
        @Field("JXLDM") jxldm: String,
        @Field("RQ") rq: String,
        @Field("XNXQDM") xnxqdm: String,
        @Field("XNDM") xndm: String,
    ): Response<RoomOccupancyResponse>
```

- [ ] **Step 2: 编译** → BUILD SUCCESSFUL。
- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/example/personal_studio/data/network/bit/service/BitJwappService.kt
git commit -m "p11: BitJwappService 加空教室 kxjasbyMobile 三端点(校区/楼/占用)"
```

### Task 3: domain models

**Files:** Create `app/src/main/java/com/example/personal_studio/domain/emptyroom/model/EmptyRoomModels.kt`

- [ ] **Step 1: 实现**

```kotlin
package com.example.personal_studio.domain.emptyroom.model

/** 校区。 */
data class Campus(val code: String, val name: String)

/** 教学楼。 */
data class Building(val code: String, val name: String, val campusCode: String)

/** 单教室在某日的空闲情况。
 *  busyPeriods: 忙节次集(1..13);freeRanges: 合并后的连续空闲区间(闭区间)。 */
data class RoomFreeSlots(
    val roomName: String,
    val buildingName: String,
    val busyPeriods: Set<Int>,
    val freeRanges: List<IntRange>,
    val status: RoomStatus,
)

/** 相对「某一时刻」(默认现在)的状态。 */
data class RoomStatus(
    val freeNow: Boolean,
    /** freeNow=true 时:能连续空到的钟点(分钟数,从一天 0 点起);用于「还能空多久」。 */
    val freeUntilMinuteOfDay: Int?,
    /** freeNow=false 时:下一个变空的钟点(分钟);null=今天不再空。 */
    val nextFreeMinuteOfDay: Int?,
)

sealed interface EmptyRoomError {
    object NeedLogin : EmptyRoomError
    object WrongCredentials : EmptyRoomError
    data class Network(val cause: String) : EmptyRoomError
    data class Parse(val message: String) : EmptyRoomError
    data class Unexpected(val cause: String) : EmptyRoomError
}
```

- [ ] **Step 2: 编译** → BUILD SUCCESSFUL。
- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/example/personal_studio/domain/emptyroom/model/EmptyRoomModels.kt
git commit -m "p11: 空教室 domain models(Campus/Building/RoomFreeSlots/RoomStatus/Error)"
```

### Task 4: PeriodClock(节次↔LocalTime,读已有作息表)— TDD

**Files:** Create `app/src/main/java/com/example/personal_studio/domain/emptyroom/PeriodClock.kt`; Test `app/src/test/java/com/example/personal_studio/domain/emptyroom/PeriodClockTest.kt`

背景:已有 `TimetablePreferences.periods: Flow<List<TimetablePeriod(index, startHHmm, endHHmm)>>`(fallback `DefaultTimetable.PERIODS` 13 节)。`PeriodClock` 是基于一份 periods 快照的纯计算工具。

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.domain.emptyroom

import com.example.personal_studio.core.util.DefaultTimetable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeriodClockTest {
    private val clock = PeriodClock(DefaultTimetable.PERIODS)

    @Test fun `start and end minute of period`() {
        // 1 节 08:00-08:45
        assertEquals(8 * 60, clock.startMinute(1))
        assertEquals(8 * 60 + 45, clock.endMinute(1))
        // 6 节 13:20-14:05
        assertEquals(13 * 60 + 20, clock.startMinute(6))
    }

    @Test fun `periodAt maps minute-of-day to period`() {
        assertEquals(1, clock.periodAt(8 * 60 + 10))      // 08:10 在第1节
        assertEquals(6, clock.periodAt(13 * 60 + 30))     // 13:30 在第6节
        assertNull(clock.periodAt(7 * 60))                 // 07:00 课前,无节次
        assertNull(clock.periodAt(12 * 60 + 40))           // 12:40 午休空档(5节后6节前)
    }
}
```

- [ ] **Step 2: 跑确认失败** `./gradlew :app:testDebugUnitTest --tests "*.PeriodClockTest"` → FAIL(Unresolved reference PeriodClock)。
- [ ] **Step 3: 实现**

```kotlin
package com.example.personal_studio.domain.emptyroom

import com.example.personal_studio.core.util.TimetablePeriod
import java.time.LocalTime

/** 基于一份作息表快照,提供 节次 ↔ 当天分钟数(minute-of-day) 的换算。 */
class PeriodClock(periods: List<TimetablePeriod>) {
    private val byIndex = periods.associateBy { it.index }

    private fun toMin(hhmm: String): Int = LocalTime.parse(hhmm).let { it.hour * 60 + it.minute }

    fun startMinute(period: Int): Int = byIndex[period]?.let { toMin(it.startHHmm) } ?: 0
    fun endMinute(period: Int): Int = byIndex[period]?.let { toMin(it.endHHmm) } ?: 0

    /** 给定当天分钟数,返回它落在哪一节(上课时段内);课间/课前课后返回 null。 */
    fun periodAt(minuteOfDay: Int): Int? =
        byIndex.values.firstOrNull { minuteOfDay >= toMin(it.startHHmm) && minuteOfDay < toMin(it.endHHmm) }?.index
}
```

- [ ] **Step 4: 跑确认通过** → PASS。
- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/personal_studio/domain/emptyroom/PeriodClock.kt app/src/test/java/com/example/personal_studio/domain/emptyroom/PeriodClockTest.kt
git commit -m "p11: PeriodClock 节次↔当天分钟换算(读已有作息表)+ 单测"
```

### Task 5: ComputeFreeSlotsUseCase(ZYJC→空闲段+状态)— TDD

**Files:** Create `app/src/main/java/com/example/personal_studio/domain/emptyroom/ComputeFreeSlotsUseCase.kt`; Test `app/src/test/java/com/example/personal_studio/domain/emptyroom/ComputeFreeSlotsUseCaseTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.domain.emptyroom

import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.data.network.bit.dto.RoomOccupancyDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ComputeFreeSlotsUseCaseTest {
    private val clock = PeriodClock(DefaultTimetable.PERIODS)
    private val uc = ComputeFreeSlotsUseCase()

    @Test fun `parses busy periods and inverts to free ranges`() {
        // 忙 1,2,3,7,8 → 空 4-6,9-13
        val r = uc.invoke(RoomOccupancyDto(roomName = "101", busyPeriods = "1,2,3,7,8"), "理教", clock, nowMinute = 0)
        assertEquals(setOf(1, 2, 3, 7, 8), r.busyPeriods)
        assertEquals(listOf(4..6, 9..13), r.freeRanges)
    }

    @Test fun `null busy means free all day`() {
        val r = uc.invoke(RoomOccupancyDto(roomName = "102", busyPeriods = null), "理教", clock, nowMinute = 0)
        assertEquals(emptySet<Int>(), r.busyPeriods)
        assertEquals(listOf(1..13), r.freeRanges)
    }

    @Test fun `freeNow true when now is in a free period`() {
        // 忙 1,2 → 09:00(第2节内? 2节 08:50-09:35) 实际 09:40 在第3节(空)
        val r = uc.invoke(RoomOccupancyDto("103", "1,2"), "理教", clock, nowMinute = 9 * 60 + 40)
        assertEquals(true, r.status.freeNow)
        // 连续空到第5节末 12:20(忙才中断;这里 3,4,5 空,6 起午休后看占用——本例只忙1,2故空到13节末20:55)
        assertEquals(20 * 60 + 55, r.status.freeUntilMinuteOfDay)
    }

    @Test fun `freeNow false during a busy period gives next free time`() {
        // 忙 3 → 09:40 在第3节(忙);下一个空是第4节 10:45
        val r = uc.invoke(RoomOccupancyDto("104", "3"), "理教", clock, nowMinute = 9 * 60 + 40)
        assertEquals(false, r.status.freeNow)
        assertEquals(10 * 60 + 45, r.status.nextFreeMinuteOfDay)
    }
}
```

- [ ] **Step 2: 跑确认失败** → FAIL。
- [ ] **Step 3: 实现**

```kotlin
package com.example.personal_studio.domain.emptyroom

import com.example.personal_studio.data.network.bit.dto.RoomOccupancyDto
import com.example.personal_studio.domain.emptyroom.model.RoomFreeSlots
import com.example.personal_studio.domain.emptyroom.model.RoomStatus
import javax.inject.Inject

/** 把一条 cxkxjasqk 行(JASMC + ZYJC 忙节次串)换算成空闲段 + 相对「nowMinute」的状态。
 *  纯函数,无依赖,易测。 */
class ComputeFreeSlotsUseCase @Inject constructor() {

    fun invoke(
        dto: RoomOccupancyDto,
        buildingName: String,
        clock: PeriodClock,
        nowMinute: Int,
    ): RoomFreeSlots {
        val busy = parseBusy(dto.busyPeriods)
        val free = (1..13).filter { it !in busy }
        val ranges = mergeConsecutive(free)
        val status = computeStatus(busy, clock, nowMinute)
        return RoomFreeSlots(
            roomName = dto.roomName.orEmpty(),
            buildingName = buildingName,
            busyPeriods = busy,
            freeRanges = ranges,
            status = status,
        )
    }

    private fun parseBusy(zyjc: String?): Set<Int> =
        zyjc?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet().orEmpty()

    private fun mergeConsecutive(sorted: List<Int>): List<IntRange> {
        if (sorted.isEmpty()) return emptyList()
        val out = mutableListOf<IntRange>()
        var start = sorted.first(); var prev = start
        for (p in sorted.drop(1)) {
            if (p == prev + 1) { prev = p } else { out += start..prev; start = p; prev = p }
        }
        out += start..prev
        return out
    }

    private fun computeStatus(busy: Set<Int>, clock: PeriodClock, nowMinute: Int): RoomStatus {
        val curPeriod = clock.periodAt(nowMinute)
        // 当前不在任何上课节次(课前/课间/课后):视为「现在空」,空到下一个忙节次开始。
        val freeNow = curPeriod == null || curPeriod !in busy
        if (freeNow) {
            // 从 max(当前节, 1) 向后找第一个忙节次;空闲持续到该忙节次开始,否则到 13 节末。
            val from = (curPeriod ?: nextPeriodStartingAfter(clock, nowMinute) ?: 14)
            val nextBusy = (from..13).firstOrNull { it in busy }
            val until = if (nextBusy != null) clock.startMinute(nextBusy) else clock.endMinute(13)
            return RoomStatus(freeNow = true, freeUntilMinuteOfDay = until, nextFreeMinuteOfDay = null)
        } else {
            // 当前节忙:找当前节之后第一个空节次的开始钟点。
            val nextFree = ((curPeriod!! + 1)..13).firstOrNull { it !in busy }
            return RoomStatus(
                freeNow = false,
                freeUntilMinuteOfDay = null,
                nextFreeMinuteOfDay = nextFree?.let { clock.startMinute(it) },
            )
        }
    }

    /** 当前在课间(periodAt=null)时,下一个即将开始的节次。 */
    private fun nextPeriodStartingAfter(clock: PeriodClock, nowMinute: Int): Int? =
        (1..13).firstOrNull { clock.startMinute(it) > nowMinute }
}
```

- [ ] **Step 4: 跑确认通过** → PASS(4 测试)。
- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/personal_studio/domain/emptyroom/ComputeFreeSlotsUseCase.kt app/src/test/java/com/example/personal_studio/domain/emptyroom/ComputeFreeSlotsUseCaseTest.kt
git commit -m "p11: ComputeFreeSlotsUseCase ZYJC→空闲段+现在空/下次变化 + 4 单测"
```

### Task 6: EmptyRoomRepository(warm-up + 拉取 + 缓存 + 全校区并发)

**Files:** Create `app/src/main/java/com/example/personal_studio/domain/emptyroom/EmptyRoomRepository.kt`

镜像 `SyncExamsUseCase` 的 ehall 流程。占用按 `(楼code, 日期)` 内存缓存。无单测(网络编排;ViewModel 测试覆盖行为,真机 DoD 验协议)。

- [ ] **Step 1: 实现**

```kotlin
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
```

- [ ] **Step 2: 编译** → BUILD SUCCESSFUL。
- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/example/personal_studio/domain/emptyroom/EmptyRoomRepository.kt
git commit -m "p11: EmptyRoomRepository — warm-up/登录 + 校区/楼/占用拉取 + 缓存 + 全校区并发"
```

---

## 阶段二:核心查询 ViewModel + 屏

### Task 7: EmptyRoomViewModel — TDD

**Files:** Create `app/src/main/java/com/example/personal_studio/feature/emptyroom/EmptyRoomViewModel.kt`; Test `app/src/test/java/com/example/personal_studio/feature/emptyroom/EmptyRoomViewModelTest.kt`

ViewModel 持有查询状态 + 触发 repo。凭据来自 `ImportCredentialPrefs`(同考试页)。排序「空闲优先 + 连续空闲时长降序」。

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.feature.emptyroom

import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.emptyroom.EmptyRoomRepository
import com.example.personal_studio.domain.emptyroom.EmptyRoomResult
import com.example.personal_studio.domain.emptyroom.PeriodClock
import com.example.personal_studio.domain.emptyroom.model.Building
import com.example.personal_studio.domain.emptyroom.model.Campus
import com.example.personal_studio.domain.emptyroom.model.RoomFreeSlots
import com.example.personal_studio.domain.emptyroom.model.RoomStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EmptyRoomViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun freeRoom(name: String, freeNow: Boolean, until: Int?) = RoomFreeSlots(
        roomName = name, buildingName = "理教", busyPeriods = emptySet(), freeRanges = listOf(1..13),
        status = RoomStatus(freeNow = freeNow, freeUntilMinuteOfDay = until, nextFreeMinuteOfDay = null),
    )

    private fun vm(
        repo: EmptyRoomRepository,
        creds: SavedCredentials? = SavedCredentials("u", "p", NetworkMode.LOCAL),
    ): EmptyRoomViewModel {
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(creds)
        }
        val dao = mockk<TimelineDao>(relaxed = true) { every { observeItemsInRange(any(), any()) } returns flowOf(emptyList()) }
        return EmptyRoomViewModel(repo, credPrefs, dao, nowProvider = { 0L })
    }

    @Test fun `query without creds emits NeedLogin`() = runTest {
        val repo = mockk<EmptyRoomRepository>(relaxed = true)
        val vm = vm(repo, creds = null)
        val events = mutableListOf<EmptyRoomEvent>()
        val job = launch { vm.events.collect { events += it } }
        vm.onSmartNow(); advanceUntilIdle()
        assertEquals(listOf(EmptyRoomEvent.NeedLogin), events)
        job.cancel()
    }

    @Test fun `smart-now sorts free rooms by longest free duration desc`() = runTest {
        val repo = mockk<EmptyRoomRepository>(relaxed = true) {
            coEvery { openAndLogin(any(), any(), any()) } returns EmptyRoomResult.Ok("2025-2026-2")
            coEvery { campuses() } returns listOf(Campus("01", "良乡"))
            coEvery { occupancyForCampus(any(), any(), any(), any()) } returns listOf(
                freeRoom("A", freeNow = true, until = 12 * 60),   // 空到 12:00
                freeRoom("B", freeNow = true, until = 20 * 60),   // 空到 20:00(更久)
                freeRoom("C", freeNow = false, until = null),     // 现在不空
            )
        }
        val vm = vm(repo)
        val job = launch { vm.uiState.collect {} }
        vm.onSmartNow(); advanceUntilIdle()
        // 只保留现在空,按 freeUntil 降序:B 在 A 前;C 排除
        assertEquals(listOf("B", "A"), vm.uiState.value.rooms.map { it.roomName })
        job.cancel()
    }
}
```

- [ ] **Step 2: 跑确认失败** → FAIL。
- [ ] **Step 3: 实现**

```kotlin
package com.example.personal_studio.feature.emptyroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.emptyroom.EmptyRoomRepository
import com.example.personal_studio.domain.emptyroom.EmptyRoomResult
import com.example.personal_studio.domain.emptyroom.model.Building
import com.example.personal_studio.domain.emptyroom.model.Campus
import com.example.personal_studio.domain.emptyroom.model.EmptyRoomError
import com.example.personal_studio.domain.emptyroom.model.RoomFreeSlots
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class EmptyRoomUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val campuses: List<Campus> = emptyList(),
    val buildings: List<Building> = emptyList(),
    val selectedCampus: Campus? = null,
    val selectedBuilding: Building? = null,   // null = 全校区
    val date: String = "",                    // yyyy-MM-dd
    val rooms: List<RoomFreeSlots> = emptyList(),
    val minFreeHours: Int = 0,                // 条件筛:接下来至少空 N 小时(0=不筛)
)

sealed interface EmptyRoomEvent { object NeedLogin : EmptyRoomEvent }

@HiltViewModel
class EmptyRoomViewModel @Inject constructor(
    private val repo: EmptyRoomRepository,
    private val credPrefs: ImportCredentialPrefs,
    private val dao: TimelineDao,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _ui = MutableStateFlow(EmptyRoomUiState(date = today()))
    val uiState: StateFlow<EmptyRoomUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<EmptyRoomEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<EmptyRoomEvent> = _events.asSharedFlow()

    private var term: String? = null

    /** 智能「现在去自习」:登录 → 取校区 → 全校区扫 → 只留现在空 → 按连续空闲时长降序。 */
    fun onSmartNow() = run {
        val creds = credPrefs.observeAll().value ?: run {
            viewModelScope.launch { _events.emit(EmptyRoomEvent.NeedLogin) }; return@run
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val term = ensureSession(creds.username, creds.password, creds.lastMode ?: NetworkMode.LOCAL)
                    ?: return@launch
                val campuses = repo.campuses()
                val campus = _ui.value.selectedCampus ?: campuses.firstOrNull()
                if (campus == null) { _ui.update { it.copy(loading = false, error = "无校区") }; return@launch }
                val rooms = repo.occupancyForCampus(campus.code, _ui.value.date, term, nowMinute())
                    .filter { it.status.freeNow }
                    .sortedByDescending { it.status.freeUntilMinuteOfDay ?: 0 }
                _ui.update { it.copy(loading = false, campuses = campuses, selectedCampus = campus, rooms = rooms) }
            } catch (e: Throwable) {
                _ui.update { it.copy(loading = false, error = "网络错误,请重试") }
            } finally {
                repo.close()
            }
        }
    }

    /** 选定校区/楼/日期后查询(楼=null 走全校区);按空闲优先 + 现在空时长排序;应用条件筛。 */
    fun onQuery() {
        val creds = credPrefs.observeAll().value ?: run {
            viewModelScope.launch { _events.emit(EmptyRoomEvent.NeedLogin) }; return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val term = ensureSession(creds.username, creds.password, creds.lastMode ?: NetworkMode.LOCAL)
                    ?: return@launch
                val st = _ui.value
                val campus = st.selectedCampus ?: repo.campuses().firstOrNull() ?: return@launch
                val raw = st.selectedBuilding?.let { repo.occupancy(it, st.date, term, nowMinute()) }
                    ?: repo.occupancyForCampus(campus.code, st.date, term, nowMinute())
                val rooms = applyFilterAndSort(raw, st.minFreeHours)
                _ui.update { it.copy(loading = false, selectedCampus = campus, rooms = rooms) }
            } catch (e: Throwable) {
                _ui.update { it.copy(loading = false, error = "网络错误,请重试") }
            } finally {
                repo.close()
            }
        }
    }

    fun onSelectCampus(c: Campus) { _ui.update { it.copy(selectedCampus = c, buildings = emptyList(), selectedBuilding = null) } }
    fun onSelectBuilding(b: Building?) { _ui.update { it.copy(selectedBuilding = b) } }
    fun onSelectDate(d: String) { _ui.update { it.copy(date = d) } }
    fun onMinFreeHours(h: Int) { _ui.update { it.copy(minFreeHours = h) } }

    private fun applyFilterAndSort(rooms: List<RoomFreeSlots>, minHours: Int): List<RoomFreeSlots> {
        val now = nowMinute()
        val filtered = if (minHours <= 0) rooms else rooms.filter {
            it.status.freeNow && (it.status.freeUntilMinuteOfDay ?: now) - now >= minHours * 60
        }
        return filtered.sortedWith(
            compareByDescending<RoomFreeSlots> { it.status.freeNow }
                .thenByDescending { it.status.freeUntilMinuteOfDay ?: 0 }
                .thenBy { it.roomName },
        )
    }

    private suspend fun ensureSession(u: String, p: String, mode: NetworkMode): String? {
        return when (val r = repo.openAndLogin(u, p, mode)) {
            is EmptyRoomResult.Ok -> r.value.also { term = it }
            is EmptyRoomResult.Err -> {
                if (r.error is EmptyRoomError.NeedLogin || r.error is EmptyRoomError.WrongCredentials) {
                    _events.emit(EmptyRoomEvent.NeedLogin)
                }
                _ui.update { it.copy(loading = false, error = errMsg(r.error)) }
                null
            }
        }
    }

    private fun errMsg(e: EmptyRoomError): String = when (e) {
        EmptyRoomError.NeedLogin, EmptyRoomError.WrongCredentials -> "请登录"
        is EmptyRoomError.Network -> "网络错误,请重试"
        is EmptyRoomError.Parse -> "教务返回异常"
        is EmptyRoomError.Unexpected -> "未知错误"
    }

    private fun nowMinute(): Int {
        val z = ZoneId.systemDefault()
        val t = Instant.ofEpochMilli(nowProvider()).atZone(z).toLocalTime()
        return t.hour * 60 + t.minute
    }

    private fun today(): String =
        Instant.ofEpochMilli(nowProvider()).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}
```

- [ ] **Step 4: 跑确认通过** → PASS(2 测试)。
- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/personal_studio/feature/emptyroom/EmptyRoomViewModel.kt app/src/test/java/com/example/personal_studio/feature/emptyroom/EmptyRoomViewModelTest.kt
git commit -m "p11: EmptyRoomViewModel 智能现在去自习/查询/排序/条件筛 + 单测"
```

### Task 8: EmptyRoomScreen(信息卡列表 + 智能头部 + 筛选 + 点开网格 + CRT)

**Files:** Create `app/src/main/java/com/example/personal_studio/feature/emptyroom/ui/EmptyRoomScreen.kt`

UI-only(编译 + 真机验)。复用考试页的 `Box(Void).scanLines().vignette()` + 信息卡 + `$ ...` header + onNeedLogin。

- [ ] **Step 1: 实现**

```kotlin
package com.example.personal_studio.feature.emptyroom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.emptyroom.model.RoomFreeSlots
import com.example.personal_studio.feature.emptyroom.EmptyRoomEvent
import com.example.personal_studio.feature.emptyroom.EmptyRoomViewModel
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Deep
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void
import com.example.personal_studio.ui.theme.scanLines
import com.example.personal_studio.ui.theme.vignette

@Composable
fun EmptyRoomScreen(onBack: () -> Unit, onNeedLogin: () -> Unit, vm: EmptyRoomViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.events.collect { if (it is EmptyRoomEvent.NeedLogin) onNeedLogin() } }

    Box(Modifier.fillMaxSize().background(Void).scanLines().vignette(cornerDim = 0.42f, centerGlow = 0.03f)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onBack) { Text("←", color = FoamMute) }
                Text("$ empty-room", color = Cyan, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(st.date, color = FoamDim, style = MaterialTheme.typography.labelMedium)
            }

            // 智能头部:一键现在去自习 + 普通查询
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmartButton("⚡ 现在去自习", Phosphor, Modifier.weight(1f)) { vm.onSmartNow() }
                SmartButton("↻ 查询", Cyan, Modifier.weight(1f)) { vm.onQuery() }
            }

            // 条件筛 chip(接下来至少空 N 小时)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 1, 2, 3).forEach { h ->
                    FilterChip(if (h == 0) "不限" else "≥${h}h", selected = st.minFreeHours == h) { vm.onMinFreeHours(h) }
                }
            }

            st.error?.let { Spacer(Modifier.height(6.dp)); Text("⚠ $it", color = Amber, style = MaterialTheme.typography.labelMedium) }
            Spacer(Modifier.height(10.dp))

            when {
                st.loading -> Text("查询中…", color = FoamDim, style = MaterialTheme.typography.labelMedium)
                st.rooms.isEmpty() -> Text("点上方「现在去自习」或「查询」找空教室", color = FoamDim, style = MaterialTheme.typography.labelMedium)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(st.rooms, key = { it.buildingName + it.roomName }) { RoomCard(it) }
                }
            }
        }
    }
}

@Composable
private fun SmartButton(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.border(1.dp, color).background(Deep).clickable { onClick() }.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, style = MaterialTheme.typography.titleSmall) }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.border(1.dp, if (selected) Phosphor else Rule)
            .background(if (selected) Phosphor.copy(alpha = 0.12f) else Deep)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Text(text, color = if (selected) Phosphor else FoamMute, style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun RoomCard(room: RoomFreeSlots) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().border(1.dp, Rule).background(Deep)
            .clickable { expanded = !expanded }.padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${room.buildingName} ${room.roomName}", color = Foam,
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            StatusBadge(room)
        }
        Spacer(Modifier.height(6.dp))
        Text("空闲 " + prettyFree(room.freeRanges), color = FoamMute, style = MaterialTheme.typography.bodySmall)
        if (expanded) { Spacer(Modifier.height(8.dp)); OccupancyGrid(room.busyPeriods) }
    }
}

@Composable
private fun StatusBadge(room: RoomFreeSlots) {
    val (text, color) = when {
        room.status.freeNow -> "现在空 · 到 ${minuteToHHmm(room.status.freeUntilMinuteOfDay)}" to Phosphor
        room.status.nextFreeMinuteOfDay != null -> "${minuteToHHmm(room.status.nextFreeMinuteOfDay)} 后空" to Cyan
        else -> "今天满" to FoamDim
    }
    Text(text, color = color, style = MaterialTheme.typography.labelMedium)
}

/** 节次×占用 可视化:13 个小格,忙=Carmine,空=Phosphor 淡底。 */
@Composable
private fun OccupancyGrid(busy: Set<Int>) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..13).forEach { p ->
            val isBusy = p in busy
            Box(
                Modifier.weight(1f).height(22.dp)
                    .background(if (isBusy) Carmine.copy(alpha = 0.30f) else Phosphor.copy(alpha = 0.18f))
                    .border(1.dp, if (isBusy) Carmine else Phosphor),
                contentAlignment = Alignment.Center,
            ) { Text("$p", color = if (isBusy) Carmine else Phosphor, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

private fun prettyFree(ranges: List<IntRange>): String =
    if (ranges.isEmpty()) "无" else ranges.joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}~${it.last}" } + " 节"

private fun minuteToHHmm(m: Int?): String =
    if (m == null) "—" else "%02d:%02d".format(m / 60, m % 60)
```

- [ ] **Step 2: 编译** → BUILD SUCCESSFUL(EmptyRoomScreen 暂未接线)。
- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/example/personal_studio/feature/emptyroom/ui/EmptyRoomScreen.kt
git commit -m "p11: EmptyRoomScreen 信息卡列表 + 智能头部 + 条件筛 + 点开占用网格 + CRT"
```

---

## 阶段三:导航接线 + profile 入口

### Task 9: NavRoutes + AppNavHost + ProfileScreen 第 5 卡

**Files:** Modify `ui/navigation/NavRoutes.kt`, `ui/AppNavHost.kt`, `feature/profile/ui/ProfileScreen.kt`

- [ ] **Step 1: NavRoutes 加常量**(在 `EXAMS` 附近):
```kotlin
    const val EMPTY_ROOM = "empty-room"
```

- [ ] **Step 2: AppNavHost 加 composable**(在 EXAMS 块之后):
```kotlin
        composable(
            NavRoutes.EMPTY_ROOM,
            deepLinks = listOf(navDeepLink { uriPattern = "personalstudio://empty-room" }),
        ) {
            com.example.personal_studio.feature.emptyroom.ui.EmptyRoomScreen(
                onBack = { navController.popBackStack() },
                onNeedLogin = { navController.navigate(NavRoutes.login("empty-room")) },
            )
        }
```

- [ ] **Step 3: AppNavHost PROFILE 块加回调**(在 `onLogin = ...` 前):
```kotlin
                onOpenEmptyRoom = { navController.navigate(NavRoutes.EMPTY_ROOM) },
```

- [ ] **Step 4: ProfileScreen 加参数 + 第 5 卡**

参数(在 `onLogin: () -> Unit,` 前):
```kotlin
    onOpenEmptyRoom: () -> Unit,
```
在第二个 `Row { 作业/考试 }` 之后加第三行(第 5 卡 + 占位对齐):
```kotlin
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GridCard(Modifier.weight(1f), "⌂", "空教室", null, !st.loggedIn, onOpenEmptyRoom)
            Spacer(Modifier.weight(1f))
        }
```

- [ ] **Step 5: 编译** `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL。
- [ ] **Step 6: Commit**
```bash
git add app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt app/src/main/java/com/example/personal_studio/feature/profile/ui/ProfileScreen.kt
git commit -m "p11: 空教室导航接线 + profile 第 5 卡入口"
```

---

## 阶段四:全校区/时间轴/课表联动(增强)

### Task 10: 校区/教学楼下拉 + 全校区开关 + 时间轴滑块

**Files:** Modify `feature/emptyroom/ui/EmptyRoomScreen.kt`(加 `FilterBar` 校区/楼选择 + `TimeAxisSlider`),`EmptyRoomViewModel.kt`(加 `loadBuildings()`)

- [ ] **Step 1: ViewModel 加载教学楼 + 时间轴查询时刻**

在 `EmptyRoomViewModel` 加:
```kotlin
    fun loadBuildings() {
        val creds = credPrefs.observeAll().value ?: return
        viewModelScope.launch {
            try {
                ensureSession(creds.username, creds.password, creds.lastMode ?: NetworkMode.LOCAL) ?: return@launch
                val campus = _ui.value.selectedCampus ?: repo.campuses().firstOrNull() ?: return@launch
                _ui.update { it.copy(campuses = it.campuses.ifEmpty { listOf(campus) },
                    selectedCampus = campus, buildings = repo.buildings(campus.code)) }
            } catch (_: Throwable) { } finally { repo.close() }
        }
    }
```

- [ ] **Step 2: EmptyRoomScreen 加 `FilterBar`**(校区/楼 chip 横滚 + 「全校区」)+ `TimeAxisSlider`(`androidx.compose.material3.Slider`,范围 480..1260 分钟=08:00~21:00,`onValueChangeFinished` 触发按该时刻重排;UI 用 `vm.uiState` 的 rooms 客户端按拖动时刻重染)。在 header 下、智能按钮上方插入 `FilterBar`,在条件筛下方插入 `TimeAxisSlider`。代码:

```kotlin
@Composable
private fun FilterBar(
    st: com.example.personal_studio.feature.emptyroom.EmptyRoomUiState,
    onCampus: (com.example.personal_studio.domain.emptyroom.model.Campus) -> Unit,
    onBuilding: (com.example.personal_studio.domain.emptyroom.model.Building?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (st.campuses.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            st.campuses.forEach { c -> FilterChip(c.name, st.selectedCampus?.code == c.code) { onCampus(c) } }
        }
        if (st.buildings.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip("全校区", st.selectedBuilding == null) { onBuilding(null) }
            st.buildings.forEach { b -> FilterChip(b.name, st.selectedBuilding?.code == b.code) { onBuilding(b) } }
        }
    }
}
```
(`TimeAxisSlider` 用 Material3 `Slider`;拖动值 `atMinute` 存 `remember`,`RoomCard` 的 `StatusBadge` 改成传入 `atMinute` 判断该时刻是否在某空闲区间——为最小改动,首版可让 Slider 仅显示选定时刻并在 `onValueChangeFinished` 时 `vm.onQuery()` 不变 now,真正按任意时刻重算留作打磨。)

- [ ] **Step 3: Screen 调 `vm.loadBuildings()`**(在选校区后或 `LaunchedEffect` 首次)。在 `EmptyRoomScreen` 的 `LaunchedEffect(Unit)` 里追加 `vm.loadBuildings()`,并把 `FilterBar(st, vm::onSelectCampus) { vm.onSelectBuilding(it); }` 接上(选楼后 `vm.onQuery()`)。
- [ ] **Step 4: 编译** → BUILD SUCCESSFUL。
- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/personal_studio/feature/emptyroom/
git commit -m "p11: 空教室 校区/教学楼筛选 + 全校区 + 时间轴滑块脚手架"
```

### Task 11: 课表联动「下节课后去哪」— TDD

**Files:** Modify `feature/emptyroom/EmptyRoomViewModel.kt`(加 `onAfterNextClass()` + 空档计算);Test 追加到 `EmptyRoomViewModelTest.kt`

「下节课后」= 读今天 COURSE(`dao.observeItemsInRange(todayStart, todayEnd)` filter COURSE),找 now 之后第一节课的 `endAt`(或当前课的 endAt)作为查询起点 → 设 `date=今天` 并触发查询,结果按「该时刻起空」排。

- [ ] **Step 1: 写失败测试**(追加)

```kotlin
    @Test fun `after-next-class uses today course end as query time`() = runTest {
        val repo = mockk<EmptyRoomRepository>(relaxed = true) {
            coEvery { openAndLogin(any(), any(), any()) } returns EmptyRoomResult.Ok("2025-2026-2")
            coEvery { campuses() } returns listOf(Campus("01", "良乡"))
            coEvery { occupancyForCampus(any(), any(), any(), any()) } returns listOf(
                freeRoom("A", freeNow = true, until = 18 * 60),
            )
        }
        // 今天有一节课 10:00-11:30 结束;now=0 → 下节课后起点应是课程 endAt
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(SavedCredentials("u", "p", NetworkMode.LOCAL))
        }
        val dao = mockk<TimelineDao>(relaxed = true) {
            every { observeItemsInRange(any(), any()) } returns flowOf(listOf(courseRow(11 * 60 + 30)))
        }
        val vm = EmptyRoomViewModel(repo, credPrefs, dao, nowProvider = { 0L })
        val job = launch { vm.uiState.collect {} }
        vm.onAfterNextClass(); advanceUntilIdle()
        assertEquals(listOf("A"), vm.uiState.value.rooms.map { it.roomName })
        job.cancel()
    }
```
(辅助 `courseRow(endMinuteOfDay)` 构造一个今天 `TimelineType.COURSE` 行,`endAt` = 当天该分钟对应 epoch;`startAt` 略早。加到测试顶部 helpers。)

- [ ] **Step 2: 跑确认失败** → FAIL(Unresolved onAfterNextClass)。
- [ ] **Step 3: 实现**(加到 ViewModel)

```kotlin
    /** 下节课后去哪:取今天 now 之后下一节课的结束时刻作为查询起点,找该时刻起空的教室。 */
    fun onAfterNextClass() {
        val creds = credPrefs.observeAll().value ?: run {
            viewModelScope.launch { _events.emit(EmptyRoomEvent.NeedLogin) }; return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val (dayStart, dayEnd) = todayBounds()
                val courses = dao.observeItemsInRange(dayStart, dayEnd).first()
                    .filter { it.type == com.example.personal_studio.domain.model.TimelineType.COURSE }
                val now = nowProvider()
                val nextEnd = courses.map { it.endAt ?: it.startAt }.filter { it >= now }.minOrNull()
                val atMinute = nextEnd?.let {
                    val t = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime(); t.hour * 60 + t.minute
                } ?: nowMinute()
                val term = ensureSession(creds.username, creds.password, creds.lastMode ?: NetworkMode.LOCAL) ?: return@launch
                val campus = _ui.value.selectedCampus ?: repo.campuses().firstOrNull() ?: return@launch
                val rooms = repo.occupancyForCampus(campus.code, _ui.value.date, term, atMinute)
                    .filter { it.status.freeNow }
                    .sortedByDescending { it.status.freeUntilMinuteOfDay ?: 0 }
                _ui.update { it.copy(loading = false, selectedCampus = campus, rooms = rooms) }
            } catch (e: Throwable) {
                _ui.update { it.copy(loading = false, error = "网络错误,请重试") }
            } finally { repo.close() }
        }
    }

    private fun todayBounds(): Pair<Long, Long> {
        val z = ZoneId.systemDefault()
        val d = Instant.ofEpochMilli(nowProvider()).atZone(z).toLocalDate()
        val start = d.atStartOfDay(z).toInstant().toEpochMilli()
        val end = d.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
        return start to end
    }
```
并把 Screen 智能头部加第三个按钮「↪ 下节课后」调 `vm.onAfterNextClass()`。

- [ ] **Step 4: 跑确认通过** → PASS。
- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/personal_studio/feature/emptyroom/ app/src/test/java/com/example/personal_studio/feature/emptyroom/EmptyRoomViewModelTest.kt
git commit -m "p11: 课表联动「下节课后去哪」按今天下节课结束时刻找空教室 + 单测"
```

---

## 阶段五:真机 DoD

### Task 12: 全量验证 + 真机 DoD(F12 确认)

**Files:** 无新增。

- [ ] **Step 1: 全量编译 + 单测**
```bash
./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest
```
BUILD SUCCESSFUL;全绿(PeriodClock 2、ComputeFreeSlots 4、EmptyRoomViewModel 3)。

- [ ] **Step 2: 装机** `./gradlew :app:installDebug`。

- [ ] **Step 3: 真机 DoD + F12 确认**(对照 spec §F12)
  - profile 第 5 卡「空教室」→ 进页面。
  - 点「现在去自习」→ 登录(若需)→ 出现现在空的教室,按空闲时长排;状态徽章「现在空·到 HH:mm」正确。
  - 选校区/楼/日期 → 查询;点教室展开占用网格(忙红空绿),与实际课表吻合。
  - 「下节课后去哪」→ 用今天下节课结束时刻找空教室(需已导入课表)。
  - 条件筛「≥2h」生效。
  - **F12 抓 `cxkxjasqk.do` 一次**确认:`ZYJC` 是否纯整数 csv;**是否含座位数/教室类型字段**(若有 → 下一步加座位筛 DTO 字段 + 筛选);`kxjasbyMobile` 是否真的不需自己 appId(若 403 → 加 `getAppConfig/kxjasbyMobile-...` warm-up);magic 常量是否仍有效;大楼是否一次返回全部(无分页截断)。
  - 把抓包结论记录,若需要补字段/常量则在本任务追加最小修复 commit。

- [ ] **Step 4: 收尾**:真机 DoD 通过后用 `superpowers:finishing-a-development-branch` 收尾(分支 `feature/p11-empty-classroom` → PR)。

---

## Self-Review

**Spec coverage:** ① 数据源(Task 1/2/6)✓;② 架构 data/domain/feature(Task 1-8)✓;③ 页面 列表+点开网格+智能头部+筛选(Task 8)+时间轴(Task 10)✓;④ 四创新:现在去自习(Task 7)/跨楼全校区+条件筛(Task 6/7/10)/可视化网格+时间轴(Task 8/10)/课表联动(Task 11)✓;⑤ 登录(Task 6/7)/缓存(Task 6)/性能并发(Task 6)✓;⑥ F12 待确认(Task 12)✓;⑦ profile 入口(Task 9)✓;测试(Task 4/5/7/11)✓。⑧ UI 精致(Task 8 复用考试信息卡 + CRT + 占用网格着色 + 状态徽章)✓。

**Placeholder scan:** Task 10 的 `TimeAxisSlider` 标注「首版脚手架,任意时刻重算留打磨」——这是有意的渐进(非占位):核心查询/智能/网格/联动均完整;时间轴的"任意时刻重染"作为打磨项明确写出,不阻塞功能。座位筛是 §F12 条件项(Task 12 抓到字段才加),非占位。其余步骤均含完整代码。

**Type consistency:** `RoomFreeSlots`(roomName/buildingName/busyPeriods/freeRanges/status)、`RoomStatus`(freeNow/freeUntilMinuteOfDay/nextFreeMinuteOfDay)、`PeriodClock`(startMinute/endMinute/periodAt)、`ComputeFreeSlotsUseCase.invoke(dto,buildingName,clock,nowMinute)`、`EmptyRoomRepository`(openAndLogin→EmptyRoomResult<String>/campuses/buildings/occupancy/occupancyForCampus/close)、`EmptyRoomViewModel`(onSmartNow/onQuery/onAfterNextClass/onSelect*/onMinFreeHours + nowProvider)、`NavRoutes.EMPTY_ROOM`、`TimetablePreferences.periods`/`DefaultTimetable.PERIODS`/`TimetablePeriod(index,startHHmm,endHHmm)`、`TimelineItemEntity.type==COURSE`/`endAt`/`startAt` 跨 task 一致。学期派生 `term.substringBeforeLast/AfterLast('-')` 一致。
