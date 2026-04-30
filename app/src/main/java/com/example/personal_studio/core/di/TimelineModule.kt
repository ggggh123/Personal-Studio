package com.example.personal_studio.core.di

import com.example.personal_studio.data.local.datastore.TimetablePreferences
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.timeline.AddCourseSeriesUseCase
import com.example.personal_studio.domain.timeline.AddTaskUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.ZoneId

@Module
@InstallIn(SingletonComponent::class)
object TimelineModule {

    @Provides
    fun provideAddTaskUseCase(
        repo: TimelineRepository,
    ): AddTaskUseCase = AddTaskUseCase(repo = repo)

    @Provides
    fun provideAddCourseSeriesUseCase(
        repo: TimelineRepository,
        prefs: TimetablePreferences,
    ): AddCourseSeriesUseCase = AddCourseSeriesUseCase(
        repo = repo,
        timetableProvider = { prefs.periods.first() },
        zone = ZoneId.systemDefault(),
    )
}
