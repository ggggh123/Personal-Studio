package com.example.personal_studio.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.personal_studio.data.local.db.entity.KbCategoryEntity
import com.example.personal_studio.data.local.db.entity.KbEntryEntity
import com.example.personal_studio.data.local.db.entity.KbEntryFtsEntity
import com.example.personal_studio.data.local.db.entity.KbRelationEntity
import com.example.personal_studio.data.local.db.entity.KbSourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KbDaoTest {

    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .addCallback(KbSeedCallback())
            .build()
    }

    @After fun tearDown() = db.close()

    @Test fun seedCategories_inserted_onCreate() = runTest {
        val all = db.kbCategoryDao().observeAll().first()
        assertEquals(7, all.size)
        assertTrue(all.all { it.seeded })
        assertEquals(setOf("数学", "物理", "化学", "生物", "英语", "编程", "其它"), all.map { it.name }.toSet())
    }

    @Test fun insertEntry_thenObserveById_returnsIt() = runTest {
        val now = System.currentTimeMillis()
        val mathId = db.kbCategoryDao().findByName("数学")!!.id
        val id = db.kbEntryDao().insert(
            KbEntryEntity(
                title = "二次函数判别式",
                categoryId = mathId,
                sourceType = KbSourceType.CHAT_MESSAGE,
                sourceChatMessageId = 42L,
                sourceChatSessionId = 7L,
                sourceScanPageId = null,
                originalImagePath = null,
                standardizedQuestion = null,
                summaryMarkdown = "## 核心概念\n…",
                createdAt = now,
                updatedAt = now,
            ),
        )
        val out = db.kbEntryDao().observe(id).first()
        assertNotNull(out)
        assertEquals("二次函数判别式", out!!.title)
        assertEquals(mathId, out.categoryId)
    }

    @Test fun observeMistakes_filtersByStandardizedQuestionNotNull() = runTest {
        val now = System.currentTimeMillis()
        val mathId = db.kbCategoryDao().findByName("数学")!!.id
        // notes
        db.kbEntryDao().insert(
            KbEntryEntity(
                title = "笔记A", categoryId = mathId, sourceType = KbSourceType.CHAT_MESSAGE,
                sourceChatMessageId = 1, sourceChatSessionId = 1, sourceScanPageId = null,
                originalImagePath = null, standardizedQuestion = null,
                summaryMarkdown = "...", createdAt = now, updatedAt = now,
            ),
        )
        // mistake
        db.kbEntryDao().insert(
            KbEntryEntity(
                title = "题目B", categoryId = mathId, sourceType = KbSourceType.SCAN,
                sourceChatMessageId = null, sourceChatSessionId = null, sourceScanPageId = 99,
                originalImagePath = "/tmp/x.jpg", standardizedQuestion = "求 x 的值",
                summaryMarkdown = "...", createdAt = now, updatedAt = now,
            ),
        )
        val mistakes = db.kbEntryDao().observeMistakes().first()
        assertEquals(1, mistakes.size)
        assertEquals("题目B", mistakes[0].title)
    }

    @Test fun ftsRoundtrip_indexAndMatch() = runTest {
        val now = System.currentTimeMillis()
        val mathId = db.kbCategoryDao().findByName("数学")!!.id
        val id = db.kbEntryDao().insert(
            KbEntryEntity(
                title = "二次函数判别式", categoryId = mathId,
                sourceType = KbSourceType.CHAT_MESSAGE, sourceChatMessageId = 1,
                sourceChatSessionId = 1, sourceScanPageId = null,
                originalImagePath = null, standardizedQuestion = null,
                summaryMarkdown = "判别式 b 平方 减 4ac",
                createdAt = now, updatedAt = now,
            ),
        )
        db.kbFtsDao().upsert(
            KbEntryFtsEntity(
                rowid = id,
                titleBigrams = "二次 次函 函数 判别 别式",
                summaryBigrams = "判别 别式",
                standardizedQuestionBigrams = "",
            ),
        )
        val hits = db.kbEntryDao().searchOnce("\"判别\" AND \"别式\"")
        assertEquals(1, hits.size)
        assertEquals(id, hits[0].id)
    }

    @Test fun deletingEntry_cascadesRelations() = runTest {
        val now = System.currentTimeMillis()
        val mathId = db.kbCategoryDao().findByName("数学")!!.id
        fun mkEntry(title: String) = KbEntryEntity(
            title = title, categoryId = mathId, sourceType = KbSourceType.CHAT_MESSAGE,
            sourceChatMessageId = 1, sourceChatSessionId = 1, sourceScanPageId = null,
            originalImagePath = null, standardizedQuestion = null,
            summaryMarkdown = "...", createdAt = now, updatedAt = now,
        )
        val a = db.kbEntryDao().insert(mkEntry("A"))
        val b = db.kbEntryDao().insert(mkEntry("B"))
        db.kbEntryDao().insertRelations(listOf(KbRelationEntity(a, b, 1f)))
        assertEquals(1, db.kbEntryDao().observeRelated(a).first().size)
        db.kbEntryDao().delete(a)
        // relation should be gone
        assertEquals(0, db.kbEntryDao().observeRelated(a).first().size)
    }
}
