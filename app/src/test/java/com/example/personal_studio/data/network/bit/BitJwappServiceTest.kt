package com.example.personal_studio.data.network.bit

import com.example.personal_studio.data.network.bit.service.BitJwappService
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

class BitJwappServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: BitJwappService

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        service = retrofit.create()
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `getCurrentTerm parses dqxnxq row`() = runBlocking {
        val body = javaClass.getResourceAsStream("/bit-fixtures/getCurrentTerm-sample.json")!!
            .bufferedReader().readText()
        server.enqueue(MockResponse().setBody(body))

        val resp = service.getCurrentTerm()

        assertEquals(true, resp.isSuccessful)
        val rows = resp.body()!!.datas.dqxnxq!!.rows
        assertEquals(1, rows.size)
        assertEquals("2024-2025-2", rows[0].code)
        assertEquals(1, rows[0].isCurrent)
    }
}
