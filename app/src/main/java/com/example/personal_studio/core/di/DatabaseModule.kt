package com.example.personal_studio.core.di

import android.content.Context
import androidx.room.Room
import com.example.personal_studio.data.local.db.AppDatabase
import com.example.personal_studio.data.local.db.KbSeedCallback
import com.example.personal_studio.data.local.db.dao.ChatMessageDao
import com.example.personal_studio.data.local.db.dao.ChatSessionDao
import com.example.personal_studio.data.local.db.dao.KbCategoryDao
import com.example.personal_studio.data.local.db.dao.KbEntryDao
import com.example.personal_studio.data.local.db.dao.KbFtsDao
import com.example.personal_studio.data.local.db.dao.ScanDocumentDao
import com.example.personal_studio.data.local.db.dao.ScanPageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addCallback(KbSeedCallback())
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideChatSessionDao(db: AppDatabase): ChatSessionDao = db.chatSessionDao()
    @Provides fun provideChatMessageDao(db: AppDatabase): ChatMessageDao = db.chatMessageDao()
    @Provides fun provideScanDocumentDao(db: AppDatabase): ScanDocumentDao = db.scanDocumentDao()
    @Provides fun provideScanPageDao(db: AppDatabase): ScanPageDao = db.scanPageDao()
    @Provides fun provideKbCategoryDao(db: AppDatabase): KbCategoryDao = db.kbCategoryDao()
    @Provides fun provideKbEntryDao(db: AppDatabase): KbEntryDao = db.kbEntryDao()
    @Provides fun provideKbFtsDao(db: AppDatabase): KbFtsDao = db.kbFtsDao()

    @Provides
    fun provideTimelineDao(db: AppDatabase): com.example.personal_studio.data.local.db.dao.TimelineDao =
        db.timelineDao()

    @Provides
    @Singleton
    fun provideChatRepository(
        sessionDao: ChatSessionDao,
        messageDao: ChatMessageDao,
    ): com.example.personal_studio.data.repository.ChatRepository =
        com.example.personal_studio.data.repository.ChatRepositoryImpl(sessionDao, messageDao)
}
