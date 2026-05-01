package com.example.personal_studio.domain.timeline

import com.example.personal_studio.data.repository.TimelineRepository
import javax.inject.Inject

class UpdateCourseSeriesUseCase @Inject constructor(
    private val repo: TimelineRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(
        seriesId: Long, title: String, instructor: String?, location: String?, notes: String?,
    ) = repo.updateSeriesAttributes(
        seriesId, title.trim(), instructor?.takeIf { it.isNotBlank() },
        location?.takeIf { it.isNotBlank() }, notes?.takeIf { it.isNotBlank() },
        nowProvider(),
    )
}
