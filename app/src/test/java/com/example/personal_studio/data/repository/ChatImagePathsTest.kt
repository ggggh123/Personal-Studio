package com.example.personal_studio.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatImagePathsTest {
    @Test fun `encode empty list is null`() {
        assertEquals(null, encodeChatImagePaths(emptyList()))
    }

    @Test fun `encode then decode round trips`() {
        val paths = listOf("/data/a/1.jpg", "/data/b/2.jpg")
        assertEquals(paths, decodeChatImagePaths(encodeChatImagePaths(paths)))
    }

    @Test fun `decode legacy bare single path yields one element`() {
        assertEquals(listOf("/legacy/old.jpg"), decodeChatImagePaths("/legacy/old.jpg"))
    }

    @Test fun `decode null or blank yields empty`() {
        assertEquals(emptyList<String>(), decodeChatImagePaths(null))
        assertEquals(emptyList<String>(), decodeChatImagePaths(""))
        assertEquals(emptyList<String>(), decodeChatImagePaths("   "))
    }
}
