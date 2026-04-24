package com.example.personal_studio.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.personal_studio.data.local.db.entity.ScanDocumentEntity
import com.example.personal_studio.data.local.db.entity.ScanPageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanDaoTest {

    private lateinit var db: AppDatabase

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
    }

    @After fun tearDown() { db.close() }

    @Test fun `insert doc then insert pages then cascade delete`() = runTest {
        val docId = db.scanDocumentDao().insert(
            ScanDocumentEntity(title = "T", createdAt = 1, updatedAt = 1, pageCount = 0, coverPageId = null)
        )
        val pageIds = db.scanPageDao().insertAll(
            listOf(
                ScanPageEntity(docId = docId, ordinal = 0, originalImagePath = "a", enhancedImagePath = "a", filter = "BW", cornersJson = null, createdAt = 1),
                ScanPageEntity(docId = docId, ordinal = 1, originalImagePath = "b", enhancedImagePath = "b", filter = "BW", cornersJson = null, createdAt = 1),
            )
        )
        assertEquals(2, db.scanPageDao().observePages(docId).first().size)

        db.scanDocumentDao().delete(docId)
        assertEquals(0, db.scanPageDao().observePages(docId).first().size)  // CASCADE
    }

    @Test fun `reorder via shiftOrdinals produces new ordering`() = runTest {
        val docId = db.scanDocumentDao().insert(
            ScanDocumentEntity(title = "T", createdAt = 1, updatedAt = 1, pageCount = 0, coverPageId = null)
        )
        val pageIds = db.scanPageDao().insertAll(
            (0..2).map { i ->
                ScanPageEntity(docId = docId, ordinal = i, originalImagePath = "$i", enhancedImagePath = "$i", filter = "BW", cornersJson = null, createdAt = 1)
            }
        )
        db.scanPageDao().shiftOrdinalsForReorder(docId, listOf(pageIds[2], pageIds[0], pageIds[1]))
        val pages = db.scanPageDao().observePages(docId).first()
        assertEquals(listOf(pageIds[2], pageIds[0], pageIds[1]), pages.map { it.id })
    }

    @Test fun `sort modes yield different orderings`() = runTest {
        val d1 = db.scanDocumentDao().insert(ScanDocumentEntity(title = "zebra", createdAt = 10, updatedAt = 30, pageCount = 0, coverPageId = null))
        val d2 = db.scanDocumentDao().insert(ScanDocumentEntity(title = "alpha", createdAt = 20, updatedAt = 20, pageCount = 0, coverPageId = null))
        val d3 = db.scanDocumentDao().insert(ScanDocumentEntity(title = "mango", createdAt = 30, updatedAt = 10, pageCount = 0, coverPageId = null))

        assertEquals(listOf(d3, d2, d1), db.scanDocumentDao().observeAllByTimeDesc().first().map { it.id })
        assertEquals(listOf(d2, d3, d1), db.scanDocumentDao().observeAllByAlphaAsc().first().map { it.id })
        assertEquals(listOf(d1, d2, d3), db.scanDocumentDao().observeAllByRecentUpdated().first().map { it.id })
    }
}
