package com.example.personal_studio.data.network.bit

import com.example.personal_studio.data.network.bit.service.BitCasService
import com.example.personal_studio.data.network.bit.service.BitJwappService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Single per-app instance that owns one OkHttpClient (Hilt-provided) and lazily
 * (re)builds a Retrofit per session against the chosen [NetworkMode]. Cookie
 * jar is shared with the underlying client and cleared on [close].
 */
@Singleton
class BitApiClient @Inject constructor(
    @Named("bit") private val client: OkHttpClient,
    private val cookieJar: BitCookieJar,
) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private var retrofit: Retrofit? = null
    private var openedMode: NetworkMode? = null

    /** Opens a session at the given [mode]. Idempotent if the same mode is
     *  re-requested; otherwise rebuilds the Retrofit instance. */
    fun open(mode: NetworkMode) {
        if (mode == openedMode) return
        retrofit = Retrofit.Builder()
            .baseUrl(BitUrlsConfig.baseFor(mode))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        openedMode = mode
    }

    /** Drops the Retrofit instance and clears the cookie jar. Subsequent
     *  access to [cas] or [jwapp] will throw until [open] is called again. */
    fun close() {
        retrofit = null
        openedMode = null
        cookieJar.clear()
    }

    val cas: BitCasService
        get() = retrofit?.create() ?: error("BitApiClient: session not open")

    val jwapp: BitJwappService
        get() = retrofit?.create() ?: error("BitApiClient: session not open")
}
