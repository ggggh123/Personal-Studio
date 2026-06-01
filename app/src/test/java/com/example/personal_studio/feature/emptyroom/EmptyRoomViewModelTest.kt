package com.example.personal_studio.feature.emptyroom

import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.emptyroom.EmptyRoomRepository
import com.example.personal_studio.domain.emptyroom.EmptyRoomResult
import com.example.personal_studio.domain.emptyroom.model.Building
import com.example.personal_studio.domain.emptyroom.model.Campus
import com.example.personal_studio.domain.emptyroom.model.EmptyRoomError
import com.example.personal_studio.domain.emptyroom.model.RoomFreeSlots
import com.example.personal_studio.domain.emptyroom.model.RoomStatus
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
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

    private val liangxiang = Campus("01", "良乡")

    private fun freeRoom(name: String, freeNow: Boolean, until: Int?) = RoomFreeSlots(
        roomName = name, buildingName = "理教", busyPeriods = emptySet(), freeRanges = listOf(1..13),
        status = RoomStatus(freeNow = freeNow, freeUntilMinuteOfDay = until, nextFreeMinuteOfDay = null),
    )

    private fun courseRow(endMinuteOfDay: Int) = TimelineItemEntity(
        id = 1, type = TimelineType.COURSE, title = "C",
        startAt = (endMinuteOfDay - 90) * 60_000L, endAt = endMinuteOfDay * 60_000L,
        isDone = false, sourceType = TimelineSource.IMPORTED_PORTAL, sourceExternalId = "c1",
        createdAt = 1L, updatedAt = 1L,
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
        courses: List<TimelineItemEntity> = emptyList(),
    ): EmptyRoomViewModel {
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(creds)
        }
        val dao = mockk<TimelineDao>(relaxed = true) { every { observeItemsInRange(any(), any()) } returns flowOf(courses) }
        return EmptyRoomViewModel(repo, credPrefs, dao, nowProvider = { 0L })
    }

    @Test fun `query without creds emits NeedLogin`() = runTest {
        val vm = vm(mockk(relaxed = true), creds = null)
        val events = mutableListOf<EmptyRoomEvent>()
        val job = launch { vm.events.collect { events += it } }
        vm.onSmartNow(); advanceUntilIdle()
        assertEquals(listOf(EmptyRoomEvent.NeedLogin), events)
        job.cancel()
    }

    @Test fun `init loads campuses for manual selection`() = runTest {
        val vm = vm(repo { coEvery { campuses() } returns listOf(liangxiang, Campus("02", "中关村")) })
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf("良乡", "中关村"), vm.uiState.value.campuses.map { it.name })
        assertEquals(null, vm.uiState.value.selectedCampus)  // 不预选,等用户手动选
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

    @Test fun `onSelectCampus loads that campus buildings`() = runTest {
        val vm = vm(repo { coEvery { buildings("01") } returns listOf(Building("J1", "理教", "01")) })
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onSelectCampus(liangxiang); advanceUntilIdle()
        assertEquals(liangxiang, vm.uiState.value.selectedCampus)
        assertEquals(listOf("理教"), vm.uiState.value.buildings.map { it.name })
        job.cancel()
    }

    @Test fun `smart-now sorts free rooms by longest free duration desc`() = runTest {
        val vm = vm(repo {
            coEvery { occupancyForCampus(any(), any(), any(), any()) } returns listOf(
                freeRoom("A", freeNow = true, until = 12 * 60),
                freeRoom("B", freeNow = true, until = 20 * 60),
                freeRoom("C", freeNow = false, until = null),
            )
        })
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onSelectCampus(liangxiang); advanceUntilIdle()
        vm.onSmartNow(); advanceUntilIdle()
        assertEquals(listOf("B", "A"), vm.uiState.value.rooms.map { it.roomName })
        job.cancel()
    }

    @Test fun `onQuery applies minFreeHours filter`() = runTest {
        val vm = vm(repo {
            coEvery { occupancyForCampus(any(), any(), any(), any()) } returns listOf(
                freeRoom("A", freeNow = true, until = 1 * 60),   // now=0 → 仅空 1h
                freeRoom("B", freeNow = true, until = 3 * 60),   // 空 3h
            )
        })
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onSelectCampus(liangxiang); advanceUntilIdle()
        vm.onMinFreeHours(2); vm.onQuery(); advanceUntilIdle()
        assertEquals(listOf("B"), vm.uiState.value.rooms.map { it.roomName })
        job.cancel()
    }

    @Test fun `after-next-class queries from today's next course end`() = runTest {
        val vm = vm(
            repo { coEvery { occupancyForCampus(any(), any(), any(), any()) } returns listOf(freeRoom("A", true, 18 * 60)) },
            courses = listOf(courseRow(11 * 60 + 30)),
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onSelectCampus(liangxiang); advanceUntilIdle()
        vm.onAfterNextClass(); advanceUntilIdle()
        assertEquals(listOf("A"), vm.uiState.value.rooms.map { it.roomName })
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
