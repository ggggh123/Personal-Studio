package com.example.personal_studio.domain.timeline

import com.example.personal_studio.data.repository.TimelineRepository
import javax.inject.Inject

class DeleteCourseSeriesUseCase @Inject constructor(
    private val repo: TimelineRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    enum class Scope { ALL, FUTURE_ONLY }

    suspend operator fun invoke(seriesId: Long, scope: Scope) {
        when (scope) {
            Scope.ALL -> repo.deleteSeriesAll(seriesId)
            Scope.FUTURE_ONLY -> repo.deleteSeriesFuture(seriesId, nowProvider())
        }
    }
}
