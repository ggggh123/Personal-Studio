package com.example.personal_studio.core.di

import android.app.AlarmManager
import android.content.Context
import com.example.personal_studio.data.local.datastore.TimetablePreferences
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.timeline.AddCourseSeriesUseCase
import com.example.personal_studio.domain.timeline.AddTaskUseCase
import com.example.personal_studio.domain.timeline.DeleteCourseSeriesUseCase
import com.example.personal_studio.domain.timeline.DeleteItemUseCase
import com.example.personal_studio.domain.timeline.ToggleDoneUseCase
import com.example.personal_studio.domain.timeline.UpdateCourseSeriesUseCase
import com.example.personal_studio.domain.timeline.UpdateItemUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimelineModule {

    @Provides
    @Singleton
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

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

    @Provides
    fun provideToggleDoneUseCase(repo: TimelineRepository): ToggleDoneUseCase =
        ToggleDoneUseCase(repo = repo)

    @Provides
    fun provideUpdateItemUseCase(repo: TimelineRepository): UpdateItemUseCase =
        UpdateItemUseCase(repo = repo)

    @Provides
    fun provideDeleteItemUseCase(repo: TimelineRepository): DeleteItemUseCase =
        DeleteItemUseCase(repo = repo)

    @Provides
    fun provideUpdateCourseSeriesUseCase(repo: TimelineRepository): UpdateCourseSeriesUseCase =
        UpdateCourseSeriesUseCase(repo = repo)

    @Provides
    fun provideDeleteCourseSeriesUseCase(repo: TimelineRepository): DeleteCourseSeriesUseCase =
        DeleteCourseSeriesUseCase(repo = repo)

    @Provides
    fun provideRecalculateCoursesUseCase(
        repo: com.example.personal_studio.data.repository.TimelineRepository,
        semester: com.example.personal_studio.data.local.datastore.SemesterPreferences,
    ): com.example.personal_studio.domain.timeline.RecalculateCoursesAfterTimetableChangeUseCase =
        com.example.personal_studio.domain.timeline.RecalculateCoursesAfterTimetableChangeUseCase(
            repo = repo,
            zone = java.time.ZoneId.systemDefault(),
            semesterProvider = { semester.startDate.first() },
        )

    /** WorkManager is still used by RescheduleAllUpcomingUseCase (boot/app-launch
     *  re-scheduling sweep) — keep the provider. ReminderWorker itself is gone;
     *  per-item scheduling now goes through AlarmManager. */
    @Provides
    fun provideWorkManager(
        @ApplicationContext ctx: Context,
    ): androidx.work.WorkManager = androidx.work.WorkManager.getInstance(ctx)

    @Provides
    @Singleton
    fun provideAlarmManager(
        @ApplicationContext context: Context,
    ): AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Provided rather than `@Inject constructor` because the use case carries
     * a `nowProvider: () -> Long = System::currentTimeMillis` default lambda
     * that Dagger doesn't honour on constructor injection.
     */
    @Provides
    fun provideScheduleRemindersUseCase(
        @ApplicationContext context: Context,
        alarmManager: AlarmManager,
    ): com.example.personal_studio.domain.timeline.ScheduleRemindersUseCase =
        com.example.personal_studio.domain.timeline.ScheduleRemindersUseCase(
            context = context,
            alarmManager = alarmManager,
        )
}
