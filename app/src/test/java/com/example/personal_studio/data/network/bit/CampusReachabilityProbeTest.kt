package com.example.personal_studio.data.network.bit

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class CampusReachabilityProbeTest {
    @Test fun `cancelling the probe cancels its in-flight HTTP request`() = runTest {
        val call = mockk<Call>(relaxed = true)
        val client = mockk<OkHttpClient> { every { newCall(any()) } returns call }
        val probe = CampusReachabilityProbe(client, "https://school.invalid/")
        val job = launch { probe.localReachable() }
        runCurrent()
        verify(exactly = 1) { call.enqueue(any()) }
        job.cancelAndJoin()
        verify(exactly = 1) { call.cancel() }
        assertTrue(job.isCancelled)
    }

    @Test fun `only direct success identifies an available campus route`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val probe = CampusReachabilityProbe(OkHttpClient.Builder().followRedirects(false).build(), server.url("/").toString())
            server.enqueue(MockResponse().setResponseCode(200))
            assertTrue(probe.localReachable())
            for (code in listOf(302, 403, 502)) {
                server.enqueue(MockResponse().setResponseCode(code).setHeader("Location", "https://sso.bit.edu.cn/cas/login"))
                assertFalse(probe.localReachable())
            }
            assertEquals(4, server.requestCount)
            repeat(4) { assertEquals("HEAD", server.takeRequest().method) }
        }
    }
}
