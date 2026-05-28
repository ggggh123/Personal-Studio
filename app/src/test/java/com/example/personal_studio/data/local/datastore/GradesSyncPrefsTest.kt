package com.example.personal_studio.data.local.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class GradesSyncPrefsTest {
    private lateinit var tmp: File
    private lateinit var prefs: GradesSyncPrefs

    @Before fun setUp() {
        tmp = File.createTempFile("grades_sync_prefs", ".preferences_pb").also { it.delete() }
        val ds = PreferenceDataStoreFactory.create { tmp }
        prefs = GradesSyncPrefs(ds)
    }
    @After fun tearDown() { tmp.delete() }

    @Test fun `defaults are off and 6h`() = runTest {
        val s = prefs.observe.first()
        assertEquals(false, s.enabled)
        assertEquals(6, s.intervalHours)
        assertEquals(null, s.lastSyncAt)
        assertEquals(emptySet<String>(), s.lastSeenSignature)
    }

    @Test fun `set and read signature round trip`() = runTest {
        prefs.setLastSeenSignature(setOf("a|b|正常|92", "x|y|重修|55"))
        val s = prefs.observe.first()
        assertEquals(setOf("a|b|正常|92", "x|y|重修|55"), s.lastSeenSignature)
    }

    @Test fun `interval and enabled persist`() = runTest {
        prefs.setEnabled(true)
        prefs.setIntervalHours(12)
        prefs.setLastSyncAt(123456L)
        val s = prefs.observe.first()
        assertEquals(true, s.enabled)
        assertEquals(12, s.intervalHours)
        assertEquals(123456L, s.lastSyncAt)
    }
}
