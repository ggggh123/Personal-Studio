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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportedRangeDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TimelineDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.timelineDao()
    }

    @After fun tearDown() { db.close() }

    private fun row(id: Long, startAt: Long, src: TimelineSource): TimelineItemEntity =
        TimelineItemEntity(
            id = id, type = TimelineType.COURSE, title = "t", description = null,
            startAt = startAt, endAt = startAt + 60_000, isDone = false, doneAt = null,
            location = null, instructor = null, notes = null, credits = null,
            seriesId = id, periodIndex = 1, periodEndIndex = 1, weekdayCode = 1,
            weekIndexInSemester = 1, colorOverride = null, sourceType = src,
            sourceExternalId = null, kbEntryIdsJson = "[]",
            createdAt = 0, updatedAt = 0,
        )

    @Test fun `count and delete affect only IMPORTED_PORTAL within range`() = runBlocking {
        dao.insertAll(listOf(
            row(1, 1_000, TimelineSource.IMPORTED_PORTAL),    // in-range imported  <- deleted
            row(2, 2_000, TimelineSource.IMPORTED_PORTAL),    // in-range imported  <- deleted
            row(3, 3_500, TimelineSource.IMPORTED_PORTAL),    // out-of-range
            row(4, 1_500, TimelineSource.MANUAL),             // in-range manual    <- kept
        ))
        val count = dao.countImportedInRange(0, 3_000)
        assertEquals(2, count)

        val deleted = dao.deleteImportedInRange(0, 3_000)
        assertEquals(2, deleted)

        val remaining = dao.observeItemsInRange(0, Long.MAX_VALUE).first()
        assertEquals(2, remaining.size)
        assertEquals(setOf(3L, 4L), remaining.map { it.id }.toSet())
    }
}
