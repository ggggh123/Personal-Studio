package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.local.db.entity.TermRankEntity
import javax.inject.Inject

/** 覆盖式写入：清空旧成绩+排名，写入新数据。成绩无手输来源，故全量覆盖。 */
class ReplaceGradesUseCase @Inject constructor(private val dao: GradesDao) {
    suspend fun invoke(entries: List<GradeEntryEntity>, ranks: List<TermRankEntity>) {
        dao.clearGrades()
        dao.clearRanks()
        dao.upsertAll(entries)
        dao.upsertRanks(ranks)
    }
}
