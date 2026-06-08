package com.example.personal_studio.feature.emptyroom

import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.emptyroom.EmptyRoomRepository
import com.example.personal_studio.domain.emptyroom.EmptyRoomResult
import com.example.personal_studio.domain.emptyroom.model.Building
import com.example.personal_studio.domain.emptyroom.model.Campus
import com.example.personal_studio.domain.emptyroom.model.EmptyRoomError
import com.example.personal_studio.domain.emptyroom.model.RoomFreeSlots
import com.example.personal_studio.domain.emptyroom.model.RoomStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.util.TimeZone

class EmptyRoomViewModelTest {
    // nowProvider={0L} 意为午夜 0 分 → 测试用 UTC 固定时区,避免主机时区(如 UTC+8)把 epoch 0 算成 08:00。
    private val originalTz = TimeZone.getDefault()

    @Before fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        TimeZone.setDefault(originalTz)
    }

    private val liangxiang = Campus("01", "良乡")
    private val zhongguancun = Campus("02", "中关村")
    private val b1 = Building("J1", "理教", "01")
    private val b2 = Building("J2", "文教", "01")

    private fun room(name: String, freeNow: Boolean, busy: Set<Int> = emptySet()) = RoomFreeSlots(
        roomName = name, buildingName = "理教", buildingCode = "J1", busyPeriods = busy, freeRanges = listOf(1..13),
        status = RoomStatus(freeNow = freeNow, freeUntilMinuteOfDay = null, nextFreeMinuteOfDay = null),
    )

    /** repo,默认登录成功 + 单校区良乡 + 空楼;各测试按需覆盖。 */
    private fun repo(block: EmptyRoomRepository.() -> Unit = {}) = mockk<EmptyRoomRepository>(relaxed = true) {
        coEvery { openAndLogin(any(), any(), any()) } returns EmptyRoomResult.Ok("2025-2026-2")
        coEvery { campuses() } returns listOf(liangxiang)
        coEvery { buildings(any()) } returns emptyList()
        block()
    }

    private fun vm(
        repo: EmptyRoomRepository,
        creds: SavedCredentials? = SavedCredentials("u", "p", NetworkMode.LOCAL),
    ): EmptyRoomViewModel {
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(creds)
        }
        return EmptyRoomViewModel(repo, credPrefs, nowProvider = { 0L })
    }

    @Test fun `query without creds emits NeedLogin`() = runTest {
        val vm = vm(mockk(relaxed = true), creds = null)
        val events = mutableListOf<EmptyRoomEvent>()
        val job = launch { vm.events.collect { events += it } }
        vm.onQuery(); advanceUntilIdle()
        assertEquals(listOf(EmptyRoomEvent.NeedLogin), events)
        job.cancel()
    }

    @Test fun `init loads campuses for manual selection`() = runTest {
        val vm = vm(repo { coEvery { campuses() } returns listOf(liangxiang, zhongguancun) })
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf("良乡", "中关村"), vm.uiState.value.campuses.map { it.name })
        assertEquals(emptySet<String>(), vm.uiState.value.selectedCampusCodes)  // 不预选,等用户手动选
        job.cancel()
    }

    @Test fun `query without selected campus prompts to pick one`() = runTest {
        val vm = vm(repo())
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onQuery(); advanceUntilIdle()
        assertEquals("请先选择校区", vm.uiState.value.error)
        job.cancel()
    }

    @Test fun `toggling a campus loads that campus buildings`() = runTest {
        val vm = vm(repo { coEvery { buildings("01") } returns listOf(b1) })
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onToggleCampus(liangxiang); advanceUntilIdle()
        assertEquals(setOf("01"), vm.uiState.value.selectedCampusCodes)
        assertEquals(listOf("理教"), vm.uiState.value.buildings.map { it.name })
        job.cancel()
    }

    @Test fun `selecting multiple campuses merges their buildings`() = runTest {
        val vm = vm(repo {
            coEvery { campuses() } returns listOf(liangxiang, zhongguancun)
            coEvery { buildings("01") } returns listOf(b1)
            coEvery { buildings("02") } returns listOf(Building("J9", "中教", "02"))
        })
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onToggleCampus(liangxiang); advanceUntilIdle()
        vm.onToggleCampus(zhongguancun); advanceUntilIdle()
        assertEquals(setOf("01", "02"), vm.uiState.value.selectedCampusCodes)
        assertEquals(listOf("理教", "中教"), vm.uiState.value.buildings.map { it.name })
        job.cancel()
    }

    @Test fun `query with all-buildings lists rooms, free-now first`() = runTest {
        val vm = vm(repo {
            coEvery { buildings("01") } returns listOf(b1, b2)
            coEvery { occupancyForBuildings(any(), any(), any(), any()) } returns listOf(
                room("A", freeNow = true, busy = setOf(1, 2, 3, 4, 5)),  // 空但忙较多
                room("B", freeNow = true, busy = setOf(1, 2)),           // 空且忙较少 → 最前
                room("C", freeNow = false),                              // 此刻占用 → 最后
            )
        })
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onToggleCampus(liangxiang); advanceUntilIdle()
        vm.onQuery(); advanceUntilIdle()
        assertEquals(listOf("B", "A", "C"), vm.uiState.value.rooms.map { it.roomName })
        assertEquals(true, vm.uiState.value.queried)
        job.cancel()
    }

    @Test fun `query with selected buildings queries only those buildings`() = runTest {
        val r = repo {
            coEvery { buildings("01") } returns listOf(b1, b2)
            coEvery { occupancyForBuildings(any(), any(), any(), any()) } returns listOf(room("A", true))
        }
        val vm = vm(r)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onToggleCampus(liangxiang); advanceUntilIdle()
        vm.onToggleBuilding(b1); advanceUntilIdle()
        vm.onQuery(); advanceUntilIdle()
        assertEquals(listOf("A"), vm.uiState.value.rooms.map { it.roomName })
        coVerify { r.occupancyForBuildings(listOf(b1), any(), any(), any()) }
        job.cancel()
    }

    @Test fun `query keeps only rooms free at all required periods`() = runTest {
        val vm = vm(repo {
            coEvery { buildings("01") } returns listOf(b1)
            coEvery { occupancyForBuildings(any(), any(), any(), any()) } returns listOf(
                room("A", freeNow = true, busy = setOf(3)),  // 第3节占用 → 被筛掉
                room("B", freeNow = true),                   // 全空 → 保留
            )
        })
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onToggleCampus(liangxiang); advanceUntilIdle()
        vm.onTogglePeriod(3); vm.onQuery(); advanceUntilIdle()
        assertEquals(listOf("B"), vm.uiState.value.rooms.map { it.roomName })
        job.cancel()
    }

    @Test fun `required periods filter uses AND semantics across multiple periods`() = runTest {
        val vm = vm(repo {
            coEvery { buildings("01") } returns listOf(b1)
            coEvery { occupancyForBuildings(any(), any(), any(), any()) } returns listOf(
                room("A", freeNow = true, busy = setOf(5)),  // 第5节占用 → 选了{3,5}时被筛掉
                room("B", freeNow = true, busy = setOf(7)),  // 第3、5节都空 → 保留
            )
        })
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onToggleCampus(liangxiang); advanceUntilIdle()
        vm.onTogglePeriod(3); vm.onTogglePeriod(5); vm.onQuery(); advanceUntilIdle()
        assertEquals(listOf("B"), vm.uiState.value.rooms.map { it.roomName })
        job.cancel()
    }

    @Test fun `deselecting a campus drops its building selection`() = runTest {
        val bCampus2 = Building("J9", "中教", "02")
        val vm = vm(repo {
            coEvery { campuses() } returns listOf(liangxiang, zhongguancun)
            coEvery { buildings("01") } returns listOf(b1)
            coEvery { buildings("02") } returns listOf(bCampus2)
        })
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onToggleCampus(liangxiang); advanceUntilIdle()
        vm.onToggleCampus(zhongguancun); advanceUntilIdle()
        vm.onToggleBuilding(bCampus2); advanceUntilIdle()           // 选中中关村的楼
        assertEquals(setOf("J9"), vm.uiState.value.selectedBuildingCodes)
        vm.onToggleCampus(zhongguancun); advanceUntilIdle()         // 撤掉中关村
        assertEquals(listOf("理教"), vm.uiState.value.buildings.map { it.name })
        assertEquals(emptySet<String>(), vm.uiState.value.selectedBuildingCodes)  // 其楼选择被清掉
        job.cancel()
    }

    @Test fun `query failure surfaces error and stops loading`() = runTest {
        val vm = vm(repo {
            coEvery { buildings("01") } returns listOf(b1)
            coEvery { occupancyForBuildings(any(), any(), any(), any()) } throws RuntimeException("boom")
        })
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onToggleCampus(liangxiang); advanceUntilIdle()
        vm.onQuery(); advanceUntilIdle()
        assertEquals("网络错误,请重试", vm.uiState.value.error)
        assertEquals(false, vm.uiState.value.loading)
        job.cancel()
    }

    @Test fun `toggling a campus clears previous query result`() = runTest {
        val vm = vm(repo {
            coEvery { buildings("01") } returns listOf(b1)
            coEvery { occupancyForBuildings(any(), any(), any(), any()) } returns listOf(room("A", true))
        })
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onToggleCampus(liangxiang); advanceUntilIdle()
        vm.onQuery(); advanceUntilIdle()
        assertEquals(listOf("A"), vm.uiState.value.rooms.map { it.roomName })
        vm.onToggleCampus(zhongguancun); advanceUntilIdle()
        assertEquals(emptyList<String>(), vm.uiState.value.rooms.map { it.roomName })
        assertEquals(false, vm.uiState.value.queried)
        job.cancel()
    }

    @Test fun `shifting date forward changes date and clears result`() = runTest {
        val vm = vm(repo {
            coEvery { buildings("01") } returns listOf(b1)
            coEvery { occupancyForBuildings(any(), any(), any(), any()) } returns listOf(room("A", true))
        })
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("1970-01-01", vm.uiState.value.date)  // nowProvider=0L @ UTC
        vm.onToggleCampus(liangxiang); advanceUntilIdle()
        vm.onQuery(); advanceUntilIdle()
        assertEquals(listOf("A"), vm.uiState.value.rooms.map { it.roomName })
        vm.onShiftDate(1); advanceUntilIdle()
        assertEquals("1970-01-02", vm.uiState.value.date)
        assertEquals(false, vm.uiState.value.queried)
        assertEquals(emptyList<String>(), vm.uiState.value.rooms.map { it.roomName })
        job.cancel()
    }

    @Test fun `shifting date below today is capped to today`() = runTest {
        val vm = vm(repo())
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onShiftDate(-5); advanceUntilIdle()
        assertEquals("1970-01-01", vm.uiState.value.date)  // 下限=今天
        job.cancel()
    }

    @Test fun `login failure during init loadCampuses emits NeedLogin`() = runTest {
        val vm = vm(repo { coEvery { openAndLogin(any(), any(), any()) } returns EmptyRoomResult.Err(EmptyRoomError.WrongCredentials) })
        val events = mutableListOf<EmptyRoomEvent>()
        val job = launch { vm.events.collect { events += it } }
        advanceUntilIdle()
        assertEquals(listOf(EmptyRoomEvent.NeedLogin), events)
        job.cancel()
    }
}
