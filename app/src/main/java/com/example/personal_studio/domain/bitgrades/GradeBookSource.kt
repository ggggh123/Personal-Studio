package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.core.di.DefaultDispatcher
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.domain.bitgrades.model.GradeItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The grade book computed once from the database and shared by every screen that shows it
 * (校园, 成绩, 目标绩点, 分享卡). Computing it walks every course (GPA, peer estimates, rank
 * estimate), so it runs on the default dispatcher, only when the grades or ranks change.
 * The last book is replayed to new collectors, so opening a grades screen again shows its
 * numbers on the first frame instead of an empty page.
 */
@Singleton
class GradeBookSource internal constructor(
    dao: GradesDao,
    computeGpa: ComputeGpaUseCase,
    dispatcher: CoroutineDispatcher,
    scope: CoroutineScope,
) {
    @Inject
    constructor(dao: GradesDao, computeGpa: ComputeGpaUseCase, @DefaultDispatcher dispatcher: CoroutineDispatcher) :
        this(dao, computeGpa, dispatcher, CoroutineScope(SupervisorJob() + dispatcher))

    /** The book, every course flattened, and when the grades were last downloaded. */
    data class Snapshot(val book: GradeBook, val all: List<GradeItem>, val fetchedAt: Long?)

    val snapshots: SharedFlow<Snapshot> = combine(dao.observeAll(), dao.observeRanks()) { grades, ranks ->
        val book = computeGpa.invoke(grades, ranks)
        Snapshot(book, book.terms.flatMap { it.courses }, grades.maxOfOrNull { it.fetchedAt })
    }
        .flowOn(dispatcher)
        // Keeps watching the database for a minute after the last screen leaves, and keeps the
        // last snapshot for the next one.
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 60_000), replay = 1)

    val books: Flow<GradeBook> = snapshots.map { it.book }
}
