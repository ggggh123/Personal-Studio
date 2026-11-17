package com.example.personal_studio.domain.auth

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesAnalysisCache
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.timeline.CancelRemindersUseCase
import javax.inject.Inject

/**
 * Removes what was downloaded from the school for the signed-in account: grades and ranks,
 * the AI grade report, imported courses, 乐学 and 新乐学 assignments and exams (with their reminders),
 * and the sync history. Manual courses, tasks and events stay.
 */
class ClearSchoolDataUseCase @Inject constructor(
    private val gradesDao: GradesDao,
    private val timelineDao: TimelineDao,
    private val cancelReminders: CancelRemindersUseCase,
    private val analysisCache: GradesAnalysisCache,
    private val gradesSyncPrefs: GradesSyncPrefs,
    private val ddlSyncPrefs: DdlSyncPrefs,
    private val loginPrefs: LoginPrefs,
) {
    suspend operator fun invoke() {
        gradesDao.clearGrades()
        gradesDao.clearRanks()
        analysisCache.clear()
        val sources = SCHOOL_SOURCES.map { it.name }
        timelineDao.idsBySources(sources).forEach { cancelReminders(it) }
        timelineDao.deleteBySources(sources)
        gradesSyncPrefs.setLastSeenSignature(emptySet())
        ddlSyncPrefs.setLastSeenUids(emptySet())
        ddlSyncPrefs.setBaselinedSources(emptySet())
        ddlSyncPrefs.setSubmittedUids(emptySet())
        loginPrefs.resetSyncHistory()
    }

    companion object {
        val SCHOOL_SOURCES = listOf(
            TimelineSource.IMPORTED_PORTAL, TimelineSource.IMPORTED_LEXUE, TimelineSource.IMPORTED_ECLASS, TimelineSource.IMPORTED_EXAM,
        )
    }
}
