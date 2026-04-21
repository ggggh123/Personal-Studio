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
        fake.openRouterApiKey.test {
            assertNull(awaitItem())
            fake.setOpenRouterApiKey("secret-key")
            assertEquals("secret-key", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing api key emits null`() = runTest {
        val fake = FakeUserPreferencesRepository()
        fake.setOpenRouterApiKey("secret-key")
        fake.openRouterApiKey.test {
            assertEquals("secret-key", awaitItem())
            fake.setOpenRouterApiKey(null)
            assertNull(awaitItem())
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
