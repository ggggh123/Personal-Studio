package com.example.personal_studio.domain.timeline

import com.example.personal_studio.data.repository.TimelineRepository
import javax.inject.Inject

class DeleteItemUseCase @Inject constructor(
    private val repo: TimelineRepository,
) {
    suspend operator fun invoke(itemId: Long) = repo.deleteById(itemId)
}
