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
 * Per-app session-scoped Retrofit factory. Owns ONE OkHttpClient + cookie jar
 * (both Hilt-singletons) but creates TWO Retrofit instances per `open()`:
 *
 *   - `casRetrofit`   → BIT 统一身份认证 host (sso.bit.edu.cn)
 *   - `jwappRetrofit` → 教务系统 host (jxzxehallapp.bit.edu.cn or WebVPN-encoded equivalent)
 *
 * Cookies set by CAS responses persist into the shared jar and are sent on
 * subsequent jwapp requests — BIT issues a parent-domain (.bit.edu.cn) cookie
 * during login that authorises jwapp access. For WebVPN, the jar additionally
 * tracks the per-gateway session cookie scoped to webvpn.bit.edu.cn.
 */
@Singleton
class BitApiClient @Inject constructor(
    @Named("bit") private val client: OkHttpClient,
    private val cookieJar: BitCookieJar,
) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val converterFactory =
        json.asConverterFactory("application/json".toMediaType())

    private var casRetrofit: Retrofit? = null
    private var jwappRetrofit: Retrofit? = null
    private var openedMode: NetworkMode? = null

    /** Opens a session at the given [mode]. Idempotent if same mode re-requested. */
    fun open(mode: NetworkMode) {
        if (mode == openedMode) return
        val hosts = BitUrlsConfig.hostsFor(mode)
        casRetrofit = newRetrofit(hosts.cas)
        jwappRetrofit = newRetrofit(hosts.jwapp)
        openedMode = mode
    }

    /** Drops both Retrofit instances and clears the cookie jar. Subsequent
     *  access to [cas] or [jwapp] will throw until [open] is called again. */
    fun close() {
        casRetrofit = null
        jwappRetrofit = null
        openedMode = null
        cookieJar.clear()
    }

    val cas: BitCasService
        get() = casRetrofit?.create() ?: error("BitApiClient: session not open")

    val jwapp: BitJwappService
        get() = jwappRetrofit?.create() ?: error("BitApiClient: session not open")

    /** 成绩查询服务，与 jwapp 同 host(jxzxehallapp)，复用同一 Retrofit。 */
    val cjcx: com.example.personal_studio.data.network.bit.service.BitCjcxService
        get() = jwappRetrofit?.create() ?: error("BitApiClient: session not open")

    private fun newRetrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(converterFactory)
        .build()
}
