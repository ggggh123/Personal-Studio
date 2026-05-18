package com.example.personal_studio.domain.timeline

import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.model.TimelineItem
import javax.inject.Inject

class UpdateItemUseCase @Inject constructor(
    private val repo: TimelineRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(item: TimelineItem) =
        repo.updateItem(item.copy(updatedAt = nowProvider()))
}
