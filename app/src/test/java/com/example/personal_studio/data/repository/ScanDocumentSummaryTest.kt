package com.example.personal_studio.data.repository

import app.cash.turbine.test
import com.example.personal_studio.domain.model.ScanFilter
import com.example.personal_studio.domain.model.SortMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanDocumentSummaryTest {
    @Test fun `cover is first page enhanced path, null when no pages`() = runTest {
        val repo = FakeScanRepository()
        val docA = repo.createPendingDocument("A")
        repo.appendPage(docA, "orig1", "enh1", ScanFilter.COLOR, null)
        repo.appendPage(docA, "orig2", "enh2", ScanFilter.COLOR, null)
        val docB = repo.createPendingDocument("B")   // 无页
        repo.observeDocumentSummaries(SortMode.TIME_DESC).test {
            val list = awaitItem()
            assertEquals("enh1", list.first { it.id == docA }.coverPath)  // 首页(ordinal 0)
            assertNull(list.first { it.id == docB }.coverPath)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `alpha sort orders by title case-insensitively`() = runTest {
        val repo = FakeScanRepository()
        repo.createPendingDocument("banana")
        repo.createPendingDocument("Apple")
        repo.observeDocumentSummaries(SortMode.ALPHA_ASC).test {
            assertEquals(listOf("Apple", "banana"), awaitItem().map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
