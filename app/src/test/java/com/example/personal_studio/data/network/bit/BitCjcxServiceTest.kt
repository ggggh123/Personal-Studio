package com.example.personal_studio.data.network.bit

import com.example.personal_studio.data.network.bit.service.BitCjcxService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create

class BitCjcxServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var service: BitCjcxService

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create()
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun `getGrades parses rows`() = runBlocking {
        val body = javaClass.getResourceAsStream("/bit-fixtures/cjcx-grades-sample.json")!!
            .bufferedReader().readText()
        server.enqueue(MockResponse().setBody(body))

        val resp = service.getGrades()

        assertEquals(true, resp.isSuccessful)
        val rows = resp.body()!!.datas.cxstuxqcj!!.rows
        assertEquals(3, rows.size)
        assertEquals("高等数学A", rows[0].courseName)
        assertEquals(5.0, rows[0].credit!!, 0.001)
        assertEquals("92", rows[0].score)
        assertEquals(4.0, rows[0].gradePoint!!, 0.001)
        assertEquals("2024-2025-1", rows[2].termCode)
    }

    @Test fun `getRankDetail parses rank row`() = runBlocking {
        val body = javaClass.getResourceAsStream("/bit-fixtures/cjcx-rank-sample.json")!!
            .bufferedReader().readText()
        server.enqueue(MockResponse().setBody(body))

        val resp = service.getRankDetail("""{"XNXQDM":"2024-2025-2"}""")

        assertEquals(true, resp.isSuccessful)
        val row = resp.body()!!.datas.cxstupm!!.rows.single()
        // Assert every mapped field so a swapped @SerialName is caught (these
        // hypothesized keys get re-verified against real data in Task 23 DoD).
        assertEquals("2024-2025-2", row.termCode)
        assertEquals(5, row.classRank)
        assertEquals(32, row.classTotal)
        assertEquals(18, row.majorRank)
        assertEquals(120, row.majorTotal)
    }
}
