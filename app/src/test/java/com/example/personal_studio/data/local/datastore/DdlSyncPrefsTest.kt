package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DdlSyncPrefsTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun newStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(tmp.newFolder(), "ddl.preferences_pb") }

    @Test fun `defaults are off and 12h and empty`() = runTest {
        val prefs = DdlSyncPrefs(newStore())
        val s = prefs.snapshot()
        assertEquals(false, s.enabled)
        assertEquals(12, s.intervalHours)
        assertNull(s.lastSyncAt)
        assertEquals(emptySet<String>(), s.lastSeenUids)
        assertNull(s.icalUrl)
    }

    @Test fun `uid set round trips`() = runTest {
        val prefs = DdlSyncPrefs(newStore())
        prefs.setLastSeenUids(setOf("a", "b", "c"))
        assertEquals(setOf("a", "b", "c"), prefs.snapshot().lastSeenUids)
    }

    @Test fun `ical url store and clear`() = runTest {
        val prefs = DdlSyncPrefs(newStore())
        prefs.setIcalUrl("https://lexue.bit.edu.cn/calendar/export_execute.php?x=1")
        assertEquals("https://lexue.bit.edu.cn/calendar/export_execute.php?x=1", prefs.snapshot().icalUrl)
        prefs.clearIcalUrl()
        assertNull(prefs.snapshot().icalUrl)
    }

    @Test fun `interval and enabled persist`() = runTest {
        val prefs = DdlSyncPrefs(newStore())
        prefs.setEnabled(true)
        prefs.setIntervalHours(24)
        val s = prefs.snapshot()
        assertEquals(true, s.enabled)
        assertEquals(24, s.intervalHours)
    }
}
