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
        fake.apiKey.test {
            assertNull(awaitItem())
            fake.setApiKey("secret-key")
            assertEquals("secret-key", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing api key emits null`() = runTest {
        val fake = FakeUserPreferencesRepository()
        fake.setApiKey("secret-key")
        fake.apiKey.test {
            assertEquals("secret-key", awaitItem())
            fake.setApiKey(null)
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `api base URL round-trips through setter and flow`() = runTest {
        val fake = FakeUserPreferencesRepository()
        fake.apiBaseUrl.test {
            assertNull(awaitItem())
            fake.setApiBaseUrl("https://api.openai.com/v1")
            assertEquals("https://api.openai.com/v1", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `model name flow emits null initially, then the saved value after set`() = runTest {
        val fake = FakeUserPreferencesRepository()
        fake.modelName.test {
            assertNull(awaitItem())
            fake.setModelName("google/gemini-2.0-flash-exp:free")
            assertEquals("google/gemini-2.0-flash-exp:free", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
