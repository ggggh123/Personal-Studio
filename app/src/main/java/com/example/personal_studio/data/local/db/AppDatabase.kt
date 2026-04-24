package com.example.personal_studio.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.personal_studio.data.local.db.dao.ChatMessageDao
import com.example.personal_studio.data.local.db.dao.ChatSessionDao
import com.example.personal_studio.data.local.db.dao.ScanDocumentDao
import com.example.personal_studio.data.local.db.dao.ScanPageDao
import com.example.personal_studio.data.local.db.entity.ChatMessageEntity
import com.example.personal_studio.data.local.db.entity.ChatSessionEntity
import com.example.personal_studio.data.local.db.entity.ScanDocumentEntity
import com.example.personal_studio.data.local.db.entity.ScanPageEntity

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        ScanDocumentEntity::class,
        ScanPageEntity::class,
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

    companion object {
        const val VERSION = 4
        const val NAME = "personal-studio.db"
    }
}
