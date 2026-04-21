package com.example.personal_studio.data.local.datastore

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserPreferencesRepositoryTest {

    @Test
    fun `api key flow emits null initially, then the saved value after set`() = runTest {
        val fake = FakeUserPreferencesRepository()
        fake.geminiApiKey.test {
            assertNull(awaitItem())
            fake.setGeminiApiKey("secret-key")
            assertEquals("secret-key", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing api key emits null`() = runTest {
        val fake = FakeUserPreferencesRepository()
        fake.setGeminiApiKey("secret-key")
        fake.geminiApiKey.test {
            assertEquals("secret-key", awaitItem())
            fake.setGeminiApiKey(null)
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
