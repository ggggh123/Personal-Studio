package com.example.personal_studio.core.di

import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.domain.bitimport.ImportCoursesUseCase
import com.example.personal_studio.domain.bitimport.MapBitCourseUseCase
import com.example.personal_studio.domain.bitimport.ReplaceImportedCoursesUseCase
import com.example.personal_studio.domain.bitimport.ResolveSemesterAnchorUseCase
import com.example.personal_studio.domain.bitimport.SsoLoginUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BitImportModule {

    // Hilt + Kotlin default lambda values do NOT work together (P4 memory).
    // Always provide an explicit nowProvider here.
    @Provides
    @Singleton
    fun provideNowProvider(): () -> Long = System::currentTimeMillis

    @Provides
    @Singleton
    fun provideImportCoursesUseCase(
        apiClient: BitApiClient,
        ssoLogin: SsoLoginUseCase,
        anchor: ResolveSemesterAnchorUseCase,
        mapper: MapBitCourseUseCase,
        replacer: ReplaceImportedCoursesUseCase,
        timelineDao: TimelineDao,
    ): ImportCoursesUseCase = ImportCoursesUseCase(
        apiClient, ssoLogin, anchor, mapper, replacer, timelineDao,
    )
}
