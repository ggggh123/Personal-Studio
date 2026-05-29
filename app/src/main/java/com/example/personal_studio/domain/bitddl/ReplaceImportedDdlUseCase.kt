package com.example.personal_studio.domain.bitddl

import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.domain.bitddl.model.DdlEvent
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.domain.timeline.RescheduleAllUpcomingUseCase
import javax.inject.Inject

/**
 * 把乐学 DDL 同步进 timeline_items(TASK + IMPORTED_LEXUE),按 sourceExternalId(=uid)去重:
 *   新 → insert;已存在 → update 标题/描述/课程名/截止,**保留本地 isDone/doneAt**;
 *   乐学侧消失 → delete。落库后调 RescheduleAllUpcomingUseCase 重排提醒。
 */
class ReplaceImportedDdlUseCase @Inject constructor(
    private val dao: TimelineDao,
    private val rescheduleAllUpcoming: RescheduleAllUpcomingUseCase,
) {
    suspend fun invoke(events: List<DdlEvent>, now: Long = System.currentTimeMillis()) {
        val existing = dao.getLexueDdls().associateBy { it.sourceExternalId }
        val incomingUids = events.map { it.uid }.toSet()

        existing.values
            .filter { it.sourceExternalId !in incomingUids }
            .forEach { dao.deleteById(it.id) }

        for (e in events) {
            val prior = existing[e.uid]
            if (prior == null) {
                dao.insertOne(
                    TimelineItemEntity(
                        type = TimelineType.TASK,
                        title = e.title,
                        description = e.description,
                        startAt = e.dueAt,
                        endAt = null,
                        isDone = false,
                        sourceType = TimelineSource.IMPORTED_LEXUE,
                        sourceExternalId = e.uid,
                        createdAt = now,
                        updatedAt = now,
                        courseName = e.course,
                    ),
                )
            } else {
                dao.update(
                    prior.copy(
                        title = e.title,
                        description = e.description,
                        startAt = e.dueAt,
                        courseName = e.course,
                        updatedAt = now,
                    ),
                )
            }
        }
        rescheduleAllUpcoming()
    }
}
