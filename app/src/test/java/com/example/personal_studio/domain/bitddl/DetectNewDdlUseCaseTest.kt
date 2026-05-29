package com.example.personal_studio.domain.bitddl

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.DdlSyncState
import com.example.personal_studio.domain.bitddl.model.DdlEvent
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectNewDdlUseCaseTest {
    private fun ev(uid: String) = DdlEvent(uid, "t", "d", "c", 1L)
    private fun state(uids: Set<String>) = DdlSyncState(true, 12, 0L, uids, null)
    private fun prefs(uids: Set<String>) = mockk<DdlSyncPrefs> { coEvery { snapshot() } returns state(uids) }

    @Test fun `first run builds baseline with no new`() = runTest {
        val r = DetectNewDdlUseCase(prefs(emptySet())).invoke(listOf(ev("a"), ev("b")))
        assertTrue(r.isFirstRun)
        assertTrue(r.newEvents.isEmpty())
        assertEquals(setOf("a", "b"), r.fullUids)
    }

    @Test fun `no new when all known`() = runTest {
        val r = DetectNewDdlUseCase(prefs(setOf("a", "b"))).invoke(listOf(ev("a"), ev("b")))
        assertEquals(false, r.isFirstRun)
        assertTrue(r.newEvents.isEmpty())
    }

    @Test fun `new uid is detected`() = runTest {
        val r = DetectNewDdlUseCase(prefs(setOf("a"))).invoke(listOf(ev("a"), ev("c")))
        assertEquals(listOf("c"), r.newEvents.map { it.uid })
    }

    @Test fun `vanished uid is not new`() = runTest {
        val r = DetectNewDdlUseCase(prefs(setOf("a", "b"))).invoke(listOf(ev("a")))
        assertTrue(r.newEvents.isEmpty())
        assertEquals(setOf("a"), r.fullUids)
    }
}
