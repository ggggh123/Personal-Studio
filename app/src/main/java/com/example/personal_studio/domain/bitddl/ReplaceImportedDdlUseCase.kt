package com.example.personal_studio.domain.bitddl

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.domain.bitddl.model.DdlEvent
import com.example.personal_studio.domain.bitddl.model.DdlSource
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.domain.timeline.CancelRemindersUseCase
import com.example.personal_studio.domain.timeline.RescheduleAllUpcomingUseCase
import javax.inject.Inject

/**
 * 把一个平台的作业同步进 timeline_items(TASK + 该平台来源),按 sourceExternalId(=uid)去重:
 *   新 → insert;已存在 → update 标题/描述/课程名/截止,**保留本地 isDone/doneAt**;
 *   该平台上消失 → delete。只动 [source] 自己的行,另一个平台的作业不受影响。
 * 新乐学告知是否已提交:新作业已提交就直接记为完成;已有作业在“刚提交”时自动打勾并取消提醒,
 * 用户之后手动取消不会再被勾上;从不自动取消完成。落库后重排提醒。
 */
class ReplaceImportedDdlUseCase @Inject constructor(
    private val dao: TimelineDao,
    private val prefs: DdlSyncPrefs,
    private val cancelReminders: CancelRemindersUseCase,
    private val rescheduleAllUpcoming: RescheduleAllUpcomingUseCase,
) {
    suspend fun invoke(source: DdlSource, events: List<DdlEvent>, now: Long = System.currentTimeMillis()) {
        val existing = dao.getImportedDdls(source.timeline.name).associateBy { it.sourceExternalId }
        val incomingUids = events.map { it.uid }.toSet()
        val wasSubmitted = if (source == DdlSource.ECLASS) prefs.snapshot().submittedUids else emptySet()

        existing.values
            .filter { it.sourceExternalId !in incomingUids }
            .forEach { cancelReminders(it.id); dao.deleteById(it.id) }

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
                        isDone = e.submitted,
                        doneAt = if (e.submitted) now else null,
                        sourceType = source.timeline,
                        sourceExternalId = e.uid,
                        createdAt = now,
                        updatedAt = now,
                        courseName = e.course,
                    ),
                )
            } else {
                val justSubmitted = e.submitted && e.uid !in wasSubmitted && !prior.isDone
                dao.update(
                    prior.copy(
                        title = e.title,
                        description = e.description,
                        startAt = e.dueAt,
                        courseName = e.course,
                        isDone = prior.isDone || justSubmitted,
                        doneAt = if (justSubmitted) now else prior.doneAt,
                        updatedAt = now,
                    ),
                )
                if (justSubmitted) cancelReminders(prior.id)
            }
        }
        if (source == DdlSource.ECLASS) {
            prefs.setSubmittedUids(events.filter { it.submitted }.map { it.uid }.toSet())
        }
        rescheduleAllUpcoming()
    }
}
