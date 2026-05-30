package com.example.personal_studio.domain.bitexam

import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.domain.bitexam.model.ExamItem
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.domain.timeline.RescheduleAllUpcomingUseCase
import javax.inject.Inject

/**
 * 把考试同步进 timeline_items(EXAM + IMPORTED_EXAM),按 sourceExternalId(=uid)去重:
 *   新→insert;已存在→update 标题/时间/地点/座位,**保留本地 isDone/doneAt**;消失→delete。
 *   落库后 RescheduleAllUpcomingUseCase 重排提醒。
 */
class ReplaceImportedExamUseCase @Inject constructor(
    private val dao: TimelineDao,
    private val rescheduleAllUpcoming: RescheduleAllUpcomingUseCase,
) {
    suspend fun invoke(exams: List<ExamItem>, now: Long = System.currentTimeMillis()) {
        val existing = dao.getImportedExams().associateBy { it.sourceExternalId }
        val incoming = exams.map { it.uid }.toSet()

        existing.values.filter { it.sourceExternalId !in incoming }.forEach { dao.deleteById(it.id) }

        for (e in exams) {
            val prior = existing[e.uid]
            val notes = e.seat?.let { "座位: $it" }
            if (prior == null) {
                dao.insertOne(
                    TimelineItemEntity(
                        type = TimelineType.EXAM,
                        title = e.course,
                        startAt = e.startAt,
                        endAt = e.endAt,
                        isDone = false,
                        location = e.location,
                        instructor = e.invigilator,
                        notes = notes,
                        sourceType = TimelineSource.IMPORTED_EXAM,
                        sourceExternalId = e.uid,
                        courseName = e.course,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            } else {
                dao.update(
                    prior.copy(
                        title = e.course,
                        startAt = e.startAt,
                        endAt = e.endAt,
                        location = e.location,
                        instructor = e.invigilator,
                        notes = notes,
                        courseName = e.course,
                        updatedAt = now,
                    ),
                )
            }
        }
        rescheduleAllUpcoming()
    }
}
