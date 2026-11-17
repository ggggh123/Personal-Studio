package com.example.personal_studio.domain.bitddl

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.DdlSyncState
import com.example.personal_studio.domain.bitddl.model.BackgroundDdlResult
import com.example.personal_studio.domain.bitddl.model.DdlEvent
import com.example.personal_studio.domain.bitddl.model.DdlSource
import com.example.personal_studio.domain.bitddl.model.DdlSyncOutcome
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
    private fun hw(id: Int) = DdlEvent("eclass-homework-$id", "t", "d", "c", 1L, DdlSource.ECLASS)
    private fun outcome(lexue: BackgroundDdlResult, eclass: BackgroundDdlResult) =
        DdlSyncOutcome(mapOf(DdlSource.LEXUE to lexue, DdlSource.ECLASS to eclass))

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

    @Test fun `新乐学's first sync is its own baseline, while 乐学 still notifies`() = runTest {
        val r = DetectNewDdlUseCase(prefs(setOf("a"))).invoke(
            outcome(BackgroundDdlResult.Ok(listOf(ev("a"), ev("b"))), BackgroundDdlResult.Ok(listOf(hw(1), hw(2)))),
        )
        assertEquals(listOf("b"), r.newEvents.map { it.uid })
        assertEquals(setOf(DdlSource.LEXUE, DdlSource.ECLASS), r.baselined)
        assertEquals(setOf("a", "b", "eclass-homework-1", "eclass-homework-2"), r.fullUids)
    }

    @Test fun `once baselined, new 新乐学 homework is new, even its first one`() = runTest {
        val prefs = mockk<DdlSyncPrefs> {
            coEvery { snapshot() } returns DdlSyncState(true, 12, 0L, setOf("a"), null, baselinedSources = setOf("LEXUE", "ECLASS"))
        }
        val r = DetectNewDdlUseCase(prefs).invoke(outcome(BackgroundDdlResult.Ok(listOf(ev("a"))), BackgroundDdlResult.Ok(listOf(hw(1)))))
        assertEquals(listOf("eclass-homework-1"), r.newEvents.map { it.uid })
        assertEquals(false, r.isFirstRun)
    }

    @Test fun `a platform that failed keeps its seen uids, so nothing is new when it comes back`() = runTest {
        val prefs = mockk<DdlSyncPrefs> {
            coEvery { snapshot() } returns DdlSyncState(
                true, 12, 0L, setOf("a", "eclass-homework-1"), null, baselinedSources = setOf("LEXUE", "ECLASS"),
            )
        }
        val r = DetectNewDdlUseCase(prefs).invoke(outcome(BackgroundDdlResult.Ok(listOf(ev("a"))), BackgroundDdlResult.Transient))
        assertEquals(setOf("a", "eclass-homework-1"), r.fullUids)
        assertTrue(r.newEvents.isEmpty())
    }
}
