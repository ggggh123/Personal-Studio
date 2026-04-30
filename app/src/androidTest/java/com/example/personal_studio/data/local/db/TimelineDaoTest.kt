package com.example.personal_studio.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TimelineDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.timelineDao()
    }

    @After fun tearDown() = db.close()

    private fun task(
        startAt: Long,
        title: String = "T",
        type: TimelineType = TimelineType.TASK,
        endAt: Long? = null,
        seriesId: Long? = null,
        weekday: Int? = null,
        periodStart: Int? = null,
        periodEnd: Int? = null,
        weekIndex: Int? = null,
    ) = TimelineItemEntity(
        type = type,
        title = title,
        description = null,
        startAt = startAt,
        endAt = endAt,
        sourceType = TimelineSource.MANUAL,
        seriesId = seriesId,
        weekdayCode = weekday,
        periodIndex = periodStart,
        periodEndIndex = periodEnd,
        weekIndexInSemester = weekIndex,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test fun insertOne_findById_round_trips() = runBlocking {
        val id = dao.insertOne(task(startAt = 100, title = "hi"))
        assertNotNull(dao.findById(id))
        assertEquals("hi", dao.findById(id)?.title)
    }

    @Test fun setDone_marks_doneAt() = runBlocking {
        val id = dao.insertOne(task(startAt = 100))
        dao.setDone(id, done = true, doneAt = 42L, now = 50L)
        val row = dao.findById(id)
        assertEquals(true, row?.isDone)
        assertEquals(42L, row?.doneAt)
    }

    @Test fun observeItemsInRange_includes_overlap_and_excludes_outside() = runBlocking {
        // Inside: starts at 10, ends at 30 -> overlaps [20, 25)
        dao.insertOne(task(startAt = 10, endAt = 30, title = "inside"))
        // Outside: ends at 19 -> < 20
        dao.insertOne(task(startAt = 0, endAt = 19, title = "before"))
        // Outside: starts at 25 -> >= 25
        dao.insertOne(task(startAt = 25, endAt = 40, title = "after"))

        val out = dao.observeItemsInRange(20, 25).first()
        assertEquals(listOf("inside"), out.map { it.title })
    }

    @Test fun maxSeriesId_returns_highest_existing() = runBlocking {
        dao.insertOne(task(startAt = 10, seriesId = 1))
        dao.insertOne(task(startAt = 20, seriesId = 5))
        dao.insertOne(task(startAt = 30, seriesId = 3))
        assertEquals(5L, dao.maxSeriesId())
    }

    @Test fun maxSeriesId_returns_null_when_empty() = runBlocking {
        assertNull(dao.maxSeriesId())
    }

    @Test fun deleteSeriesFuture_keeps_past_rows() = runBlocking {
        dao.insertOne(task(startAt = 10, endAt = 20, seriesId = 7, title = "past"))
        dao.insertOne(task(startAt = 100, endAt = 200, seriesId = 7, title = "future"))
        dao.deleteSeriesFuture(seriesId = 7, now = 50)
        val rows = dao.observeItemsInRange(0, Long.MAX_VALUE).first()
        assertEquals(listOf("past"), rows.map { it.title })
    }

    @Test fun observeCourseSeriesList_aggregates_count_and_weeks() = runBlocking {
        dao.insertOne(task(startAt = 10, type = TimelineType.COURSE, seriesId = 1, weekIndex = 1, weekday = 1, periodStart = 1, periodEnd = 1))
        dao.insertOne(task(startAt = 20, type = TimelineType.COURSE, seriesId = 1, weekIndex = 2, weekday = 1, periodStart = 1, periodEnd = 1))
        dao.insertOne(task(startAt = 30, type = TimelineType.COURSE, seriesId = 1, weekIndex = 3, weekday = 1, periodStart = 1, periodEnd = 1))
        val rows = dao.observeCourseSeriesList().first()
        assertEquals(1, rows.size)
        assertEquals(3, rows[0].occurrenceCount)
        assertEquals(1, rows[0].minWeek)
        assertEquals(3, rows[0].maxWeek)
    }

    @Test fun findCourseConflicts_detects_overlap_in_same_weekday_and_week_range() = runBlocking {
        // Existing: Mon, period 3-4, weeks 1-16
        dao.insertOne(task(
            startAt = 10, type = TimelineType.COURSE, seriesId = 1,
            weekday = 1, periodStart = 3, periodEnd = 4, weekIndex = 1,
        ))
        // New conflict: Mon, period 4 (overlap), weeks 8-10 (within existing range)
        val hits = dao.findCourseConflicts(
            weekday = 1, periodStart = 4, periodEnd = 5,
            weekStart = 8, weekEnd = 10,
        )
        assertEquals(1, hits.size)
    }
}
