package com.example.personal_studio.domain.timeline

import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.model.TimelineType
import javax.inject.Inject

class ToggleDoneUseCase @Inject constructor(
    private val repo: TimelineRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(itemId: Long, done: Boolean) {
        val item = repo.findById(itemId) ?: return
        require(item.type != TimelineType.COURSE) { "COURSE cannot be marked done" }
        val now = nowProvider()
        repo.setDone(itemId, done = done, doneAt = if (done) now else null, now = now)
    }
}
