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
import com.example.personal_studio.domain.emptyroom.model.EmptyRoomError
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
                freeRoom("A", freeNow = true, until = 12 * 60),
                freeRoom("B", freeNow = true, until = 20 * 60),
                freeRoom("C", freeNow = false, until = null),
            )
        }
        val vm = vm(repo)
        val job = launch { vm.uiState.collect {} }
        vm.onSmartNow(); advanceUntilIdle()
        assertEquals(listOf("B", "A"), vm.uiState.value.rooms.map { it.roomName })
        job.cancel()
    }

    @Test fun `onQuery applies minFreeHours filter`() = runTest {
        val repo = mockk<EmptyRoomRepository>(relaxed = true) {
            coEvery { openAndLogin(any(), any(), any()) } returns EmptyRoomResult.Ok("2025-2026-2")
            coEvery { campuses() } returns listOf(Campus("01", "良乡"))
            coEvery { occupancyForCampus(any(), any(), any(), any()) } returns listOf(
                freeRoom("A", freeNow = true, until = 1 * 60),   // now=0 → 仅空 1h
                freeRoom("B", freeNow = true, until = 3 * 60),   // 空 3h
            )
        }
        val vm = vm(repo)
        val job = launch { vm.uiState.collect {} }
        vm.onMinFreeHours(2); vm.onQuery(); advanceUntilIdle()
        assertEquals(listOf("B"), vm.uiState.value.rooms.map { it.roomName })  // 仅 ≥2h 的 B
        job.cancel()
    }

    @Test fun `onQuery with no campus sets error not stuck loading`() = runTest {
        val repo = mockk<EmptyRoomRepository>(relaxed = true) {
            coEvery { openAndLogin(any(), any(), any()) } returns EmptyRoomResult.Ok("2025-2026-2")
            coEvery { campuses() } returns emptyList()
        }
        val vm = vm(repo)
        val job = launch { vm.uiState.collect {} }
        vm.onQuery(); advanceUntilIdle()
        assertEquals(false, vm.uiState.value.loading)
        assertEquals("无校区", vm.uiState.value.error)
        job.cancel()
    }

    @Test fun `login failure emits NeedLogin`() = runTest {
        val repo = mockk<EmptyRoomRepository>(relaxed = true) {
            coEvery { openAndLogin(any(), any(), any()) } returns EmptyRoomResult.Err(EmptyRoomError.WrongCredentials)
        }
        val vm = vm(repo)
        val events = mutableListOf<EmptyRoomEvent>()
        val job = launch { vm.events.collect { events += it } }
        vm.onSmartNow(); advanceUntilIdle()
        assertEquals(listOf(EmptyRoomEvent.NeedLogin), events)
        job.cancel()
    }
}
