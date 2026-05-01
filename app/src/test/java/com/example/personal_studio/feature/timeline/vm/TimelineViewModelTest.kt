package com.example.personal_studio.feature.timeline.vm

import app.cash.turbine.test
import com.example.personal_studio.data.local.datastore.NotifPreferences
import com.example.personal_studio.data.local.datastore.NotifSwitches
import com.example.personal_studio.data.repository.FakeTimelineRepository
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val zone = ZoneId.of("Asia/Shanghai")

    @Before fun setUp() = kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    @After fun tearDown() = kotlinx.coroutines.Dispatchers.resetMain()

    @Test fun `selecting day changes items list`() = runTest {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val repo = FakeTimelineRepository().apply {
            preload(listOf(
                makeItem(id = 1, startAt = today.atStartOfDay(zone).toInstant().toEpochMilli() + 9 * 3600_000),
                makeItem(id = 2, startAt = tomorrow.atStartOfDay(zone).toInstant().toEpochMilli() + 10 * 3600_000),
            ))
        }
        val notif = mockk<NotifPreferences>(relaxed = true)
        every { notif.switches } returns MutableStateFlow(NotifSwitches(true, true, true, false))

        val vm = TimelineViewModel(repo, notif)
        vm.uiState.test {
            // initial: today's item
            var s = awaitItem()
            // wait for non-empty items
            while (s.items.isEmpty()) s = awaitItem()
            assertEquals(listOf(1L), s.items.map { it.id })

            vm.onNextDay()
            s = awaitItem()
            while (s.displayDay != tomorrow) s = awaitItem()
            while (s.items.map { it.id } != listOf(2L)) s = awaitItem()
            assertEquals(listOf(2L), s.items.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun makeItem(id: Long, startAt: Long) = TimelineItem(
        id = id, type = TimelineType.TASK, title = "t$id", description = null,
        startAt = startAt, endAt = null, isDone = false, doneAt = null,
        location = null, instructor = null, notes = null,
        seriesId = null, periodIndex = null, periodEndIndex = null,
        weekdayCode = null, weekIndexInSemester = null, colorOverride = null,
        sourceType = TimelineSource.MANUAL, sourceExternalId = null,
        kbEntryIds = emptyList(), createdAt = 0, updatedAt = 0,
    )
}
