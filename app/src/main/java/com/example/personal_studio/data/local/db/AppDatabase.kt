package com.example.personal_studio.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.personal_studio.data.local.db.dao.ChatMessageDao
import com.example.personal_studio.data.local.db.dao.ChatSessionDao
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.dao.KbCategoryDao
import com.example.personal_studio.data.local.db.dao.KbEntryDao
import com.example.personal_studio.data.local.db.dao.KbFtsDao
import com.example.personal_studio.data.local.db.dao.ScanDocumentDao
import com.example.personal_studio.data.local.db.dao.ScanPageDao
import com.example.personal_studio.data.local.db.entity.ChatMessageEntity
import com.example.personal_studio.data.local.db.entity.ChatSessionEntity
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.local.db.entity.KbCategoryEntity
import com.example.personal_studio.data.local.db.entity.KbEntryEntity
import com.example.personal_studio.data.local.db.entity.KbEntryFtsEntity
import com.example.personal_studio.data.local.db.entity.KbRelationEntity
import com.example.personal_studio.data.local.db.entity.ScanDocumentEntity
import com.example.personal_studio.data.local.db.entity.ScanPageEntity
import com.example.personal_studio.data.local.db.entity.TermRankEntity
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        ScanDocumentEntity::class,
        ScanPageEntity::class,
        KbCategoryEntity::class,
        KbEntryEntity::class,
        KbRelationEntity::class,
        KbEntryFtsEntity::class,
        TimelineItemEntity::class,
        GradeEntryEntity::class,
        TermRankEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun scanDocumentDao(): ScanDocumentDao
    abstract fun scanPageDao(): ScanPageDao
    abstract fun kbCategoryDao(): KbCategoryDao
    abstract fun kbEntryDao(): KbEntryDao
    abstract fun kbFtsDao(): KbFtsDao
    abstract fun timelineDao(): com.example.personal_studio.data.local.db.dao.TimelineDao
    abstract fun gradesDao(): GradesDao

    companion object {
        const val VERSION = 10
        const val NAME = "personal-studio.db"

        /** Default seed inserted by [KbSeedCallback] on first DB creation. */
        val DEFAULT_KB_CATEGORIES: List<String> = listOf(
            "数学", "物理", "化学", "生物", "英语", "编程", "其它",
        )
    }
}
