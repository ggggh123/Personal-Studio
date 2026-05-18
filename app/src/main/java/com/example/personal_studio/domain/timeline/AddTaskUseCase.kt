package com.example.personal_studio.domain.timeline

import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import javax.inject.Inject

class AddTaskUseCase @Inject constructor(
    private val repo: TimelineRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(
        type: TimelineType,
        title: String,
        description: String?,
        startAt: Long,
        endAt: Long?,
        location: String?,
    ): Long {
        require(type == TimelineType.TASK || type == TimelineType.CUSTOM) {
            "AddTaskUseCase only handles TASK / CUSTOM, was $type"
        }
        val now = nowProvider()
        val item = TimelineItem(
            id = 0,
            type = type,
            title = title.trim(),
            description = description?.takeIf { it.isNotBlank() },
            startAt = startAt,
            endAt = if (type == TimelineType.TASK) null else endAt,
            isDone = false,
            doneAt = null,
            location = location?.takeIf { it.isNotBlank() },
            instructor = null,
            notes = null,
            seriesId = null,
            periodIndex = null,
            periodEndIndex = null,
            weekdayCode = null,
            weekIndexInSemester = null,
            colorOverride = null,
            sourceType = TimelineSource.MANUAL,
            sourceExternalId = null,
            kbEntryIds = emptyList(),
            createdAt = now,
            updatedAt = now,
        )
        return repo.insertItems(listOf(item)).first()
    }
}
