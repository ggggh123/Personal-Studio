package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LoginPrefsTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun newStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(tmp.newFolder(), "login.preferences_pb") }

    @Test fun `default hasSeenLogin is false`() = runTest {
        assertEquals(false, LoginPrefs(newStore()).snapshot())
    }

    @Test fun `setHasSeenLogin persists`() = runTest {
        val prefs = LoginPrefs(newStore())
        prefs.setHasSeenLogin(true)
        assertEquals(true, prefs.snapshot())
    }
}
