package com.example.personal_studio.feature.emptyroom

import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.data.local.datastore.EmptyRoomPrefs
import com.example.personal_studio.data.local.datastore.EmptyRoomSelection
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.local.datastore.TimetablePreferences
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitimport.ResolveNetworkModeUseCase
import com.example.personal_studio.domain.emptyroom.EmptyRoomRepository
import com.example.personal_studio.domain.emptyroom.EmptyRoomResult
import com.example.personal_studio.domain.emptyroom.TimeFilter
import com.example.personal_studio.domain.emptyroom.model.Building
import com.example.personal_studio.domain.emptyroom.model.Campus
import com.example.personal_studio.domain.emptyroom.model.EmptyRoomCatalog
import com.example.personal_studio.domain.emptyroom.model.EmptyRoomError
import com.example.personal_studio.domain.emptyroom.model.RoomFreeSlots
import com.example.personal_studio.domain.emptyroom.model.RoomStatus
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class EmptyRoomViewModelTest {
    // now = 0 means midnight UTC on 1970-01-01; the zone is pinned so the host's offset doesn't move it.
    private val originalTz = TimeZone.getDefault()
    private val now = 10L * 24 * 3600 * 1000  // day 10, 00:00

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
    private val lijiao = Building("J1", "理教", "01")
    private val zongjiao = Building("J2", "综教", "01")
    private val zgc = Building("Z1", "中教", "02")
    private val freshCatalog = EmptyRoomCatalog(listOf(liangxiang, zhongguancun), listOf(lijiao, zongjiao, zgc), fetchedAt = now)

    private fun room(name: String, building: Building = lijiao, busy: Set<Int> = emptySet()) = RoomFreeSlots(
        roomName = name, buildingName = building.name, buildingCode = building.code, busyPeriods = busy,
        freeRanges = listOf(1..13), status = RoomStatus(freeNow = true, freeUntilMinuteOfDay = null, nextFreeMinuteOfDay = null),
    )

    private fun repo(block: EmptyRoomRepository.() -> Unit = {}) = mockk<EmptyRoomRepository>(relaxed = true) {
        coEvery { openAndLogin(any(), any(), any()) } returns EmptyRoomResult.Ok("2025-2026-2")
        coEvery { fetchCatalog(any()) } returns freshCatalog
        coEvery { cachedOccupancy(any(), any(), any()) } returns null
        coEvery { occupancyForBuildings(any(), any(), any(), any()) } returns emptyList()
        block()
    }

    private fun prefs(catalog: EmptyRoomCatalog? = freshCatalog, selection: EmptyRoomSelection = EmptyRoomSelection()) =
        mockk<EmptyRoomPrefs>(relaxed = true) {
            coEvery { catalog() } returns catalog
            coEvery { selection() } returns selection
        }

    private fun vm(
        repo: EmptyRoomRepository,
        prefs: EmptyRoomPrefs = prefs(),
        creds: SavedCredentials? = SavedCredentials("u", "p", NetworkMode.LOCAL),
    ): EmptyRoomViewModel {
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true) { every { observeAll() } returns MutableStateFlow(creds) }
        val resolve = mockk<ResolveNetworkModeUseCase> { coEvery { this@mockk.invoke(any()) } returns NetworkMode.LOCAL }
        val timetable = mockk<TimetablePreferences> { every { periods } returns flowOf(DefaultTimetable.PERIODS) }
        return EmptyRoomViewModel(repo, credPrefs, resolve, prefs, timetable, nowProvider = { now })
    }

    @Test fun `without an account nothing signs in and the page asks to log in`() = runTest {
        val r = repo()
        val vm = vm(r, prefs(catalog = null), creds = null)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.loggedIn)
        coVerify(exactly = 0) { r.openAndLogin(any(), any(), any()) }
    }

    @Test fun `a stored catalog opens the page without signing in`() = runTest {
        val r = repo()
        val vm = vm(r)
        advanceUntilIdle()
        assertEquals(listOf("良乡", "中关村"), vm.uiState.value.catalog?.campuses?.map { it.name })
        assertEquals(null, vm.uiState.value.campusCode)   // two campuses, nothing remembered: the user picks
        coVerify(exactly = 0) { r.openAndLogin(any(), any(), any()) }
    }

    @Test fun `no catalog yet fetches it, and a remembered place is queried in the same session`() = runTest {
        val r = repo { coEvery { occupancyForBuildings(any(), any(), any(), any()) } returns listOf(room("101")) }
        val vm = vm(r, prefs(catalog = null, selection = EmptyRoomSelection(campusCode = "01")))
        advanceUntilIdle()
        coVerify(exactly = 1) { r.openAndLogin(any(), any(), any()) }
        coVerify(exactly = 1) { r.fetchCatalog(any()) }
        coVerify { r.occupancyForBuildings(listOf(lijiao, zongjiao), "1970-01-11", "2025-2026-2", any()) }
        assertEquals(1, vm.uiState.value.results?.matching)
    }

    @Test fun `a stale catalog is fetched again`() = runTest {
        val r = repo()
        vm(r, prefs(catalog = freshCatalog.copy(fetchedAt = now - EmptyRoomViewModel.CATALOG_TTL_MS - 1)))
        advanceUntilIdle()
        coVerify(exactly = 1) { r.fetchCatalog(any()) }
    }

    @Test fun `rooms already in memory need no sign-in`() = runTest {
        val r = repo { coEvery { cachedOccupancy(any(), any(), any()) } returns listOf(room("101"), room("102")) }
        val vm = vm(r, prefs(selection = EmptyRoomSelection(campusCode = "01")))
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.results?.total)
        coVerify(exactly = 0) { r.openAndLogin(any(), any(), any()) }
    }

    @Test fun `picking a place queries only its buildings with one sign-in and remembers it`() = runTest {
        val p = prefs()
        val r = repo { coEvery { occupancyForBuildings(any(), any(), any(), any()) } returns listOf(room("101")) }
        val vm = vm(r, p)
        advanceUntilIdle()
        vm.onPickPlace("01", setOf("J1")); advanceUntilIdle()
        coVerify(exactly = 1) { r.openAndLogin(any(), any(), any()) }
        coVerify { r.occupancyForBuildings(listOf(lijiao), any(), any(), any()) }
        coVerify { p.saveSelection(match { it.campusCode == "01" && it.buildingCodes == setOf("J1") && it.recentBuildingCodes == listOf("J1") }) }
        assertEquals(listOf("理教" to 1), vm.uiState.value.results?.groups?.map { it.first to it.second.size })
    }

    @Test fun `changing the time filter re-filters without signing in again`() = runTest {
        val rooms = listOf(room("101", busy = setOf(1)), room("102", busy = setOf(7)), room("103"))
        val r = repo { coEvery { occupancyForBuildings(any(), any(), any(), any()) } returns rooms }
        val vm = vm(r, prefs(selection = EmptyRoomSelection(campusCode = "01", timeFilter = "ANY")))
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.results?.matching)
        vm.onTimeFilter(TimeFilter.MORNING); advanceUntilIdle()
        assertEquals(listOf("102", "103"), vm.uiState.value.results?.groups?.flatMap { g -> g.second.map { it.roomName } }?.sorted())
        vm.onCustomPeriods(setOf(7, 8)); advanceUntilIdle()
        assertEquals(TimeFilter.CUSTOM, vm.uiState.value.timeFilter)
        assertEquals(listOf("101", "103"), vm.uiState.value.results?.groups?.flatMap { g -> g.second.map { it.roomName } }?.sorted())
        // Two blocks: 1–2 and 7–8 must both be free.
        vm.onCustomPeriods(setOf(1, 2, 7, 8)); advanceUntilIdle()
        assertEquals(listOf("103"), vm.uiState.value.results?.groups?.flatMap { g -> g.second.map { it.roomName } })
        coVerify(exactly = 1) { r.openAndLogin(any(), any(), any()) }
    }

    @Test fun `another day fetches that day, never before today`() = runTest {
        val r = repo()
        val vm = vm(r, prefs(selection = EmptyRoomSelection(campusCode = "01")))
        advanceUntilIdle()
        vm.onDate(vm.uiState.value.today.plusDays(1)); advanceUntilIdle()
        coVerify { r.occupancyForBuildings(any(), "1970-01-12", any(), any()) }
        vm.onDate(vm.uiState.value.today.minusDays(3)); advanceUntilIdle()
        assertEquals(vm.uiState.value.today, vm.uiState.value.date)
    }

    @Test fun `refresh drops the cached rooms and fetches again`() = runTest {
        val r = repo()
        val vm = vm(r, prefs(selection = EmptyRoomSelection(campusCode = "01")))
        advanceUntilIdle()
        vm.onRefresh(); advanceUntilIdle()
        coVerify { r.dropOccupancy(listOf(lijiao, zongjiao), "1970-01-11") }
        coVerify(exactly = 2) { r.openAndLogin(any(), any(), any()) }
    }

    @Test fun `a rejected password asks to log in again`() = runTest {
        val r = repo { coEvery { openAndLogin(any(), any(), any()) } returns EmptyRoomResult.Err(EmptyRoomError.WrongCredentials) }
        val vm = vm(r, prefs(selection = EmptyRoomSelection(campusCode = "01")))
        val events = mutableListOf<EmptyRoomEvent>()
        val job = launch { vm.events.collect { events += it } }
        advanceUntilIdle()
        assertEquals(listOf(EmptyRoomEvent.NeedLogin), events)
        assertEquals("需要重新登录学校账户", vm.uiState.value.error)
        assertFalse(vm.uiState.value.loading)
        job.cancel()
    }
}
