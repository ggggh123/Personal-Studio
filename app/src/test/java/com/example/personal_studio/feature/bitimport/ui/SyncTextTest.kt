package com.example.personal_studio.feature.bitimport.ui

import com.example.personal_studio.domain.bitimport.model.SyncAllProgress
import com.example.personal_studio.domain.bitimport.model.SyncFailure
import com.example.personal_studio.domain.bitimport.model.SyncSource
import com.example.personal_studio.domain.bitimport.model.SyncSourceState
import com.example.personal_studio.domain.bitimport.model.SyncSourceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTextTest {
    private val now = 10 * 3_600_000L

    private fun progress(done: Boolean, vararg s: Pair<SyncSource, SyncSourceStatus>) = SyncAllProgress(
        SyncSource.values().associateWith { src -> SyncSourceState(s.toMap()[src] ?: SyncSourceStatus.OK) }, done,
    )

    @Test fun `subtitle while idle, running and done`() {
        assertEquals("还没有同步过", SyncText.subtitle(null, null, now))
        assertEquals("上次同步 · 2 小时前", SyncText.subtitle(null, now - 2 * 3_600_000L, now))
        val running = progress(false, SyncSource.EXAMS to SyncSourceStatus.RUNNING, SyncSource.GRADES to SyncSourceStatus.PENDING)
        assertEquals("第 3 项，共 4 项", SyncText.subtitle(running, null, now))
        assertEquals("全部完成 · 刚刚", SyncText.subtitle(progress(true), now, now))
        assertEquals("3 项完成 · 1 项需要处理", SyncText.subtitle(progress(true, SyncSource.GRADES to SyncSourceStatus.FAILED), now, now))
    }

    @Test fun `failures that need the login page`() {
        assertTrue(SyncText.needsLogin(SyncFailure.VERIFY))
        assertTrue(SyncText.needsLogin(SyncFailure.PASSWORD))
        assertFalse(SyncText.needsLogin(SyncFailure.NETWORK))
        assertEquals("需要先在教务系统完成评教", SyncText.failure(SyncFailure.REVIEW))
    }
}
