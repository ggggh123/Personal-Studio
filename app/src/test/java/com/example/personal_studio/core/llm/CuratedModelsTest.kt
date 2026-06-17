package com.example.personal_studio.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CuratedModelsTest {

    @Test fun `has 11 curated models`() {
        assertEquals(11, CuratedModels.ALL.size)
    }

    @Test fun `displayFor maps known code to display name`() {
        assertEquals("Gemini 3.5 Flash", CuratedModels.displayFor("gemini-3.5-flash"))
        assertEquals("豆包", CuratedModels.displayFor("doubao-seed-2-0-lite-260428"))
    }

    @Test fun `displayFor returns null for unknown or null code`() {
        assertNull(CuratedModels.displayFor("some-custom-model"))
        assertNull(CuratedModels.displayFor(null))
    }
}
