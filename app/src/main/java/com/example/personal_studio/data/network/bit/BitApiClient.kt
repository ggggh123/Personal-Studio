package com.example.personal_studio.data.network.bit

import com.example.personal_studio.data.network.bit.service.BitJwappService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Shared service client. Callers hold the session mutex from open through close.
 * CAS redirects use [auth]; the three Retrofit services fetch authenticated data.
 * Native and WebView requests share the app's browser cookies so an official
 * SMS verification can survive a foreground sync or background worker finishing.
 */
@Singleton
class BitApiClient internal constructor(
    private val client: OkHttpClient,
    private val cookieJar: BitCookieJar,
    private val browserSessions: BitBrowserSessions?,
    authUrls: BitAuthUrls,
) {
    @Inject constructor(
        @Named("bit") client: OkHttpClient,
        cookieJar: BitCookieJar,
        browserSessions: BitBrowserSessions,
    ) : this(client, cookieJar, browserSessions, BitAuthUrls())

    internal constructor(client: OkHttpClient, cookieJar: BitCookieJar, urls: BitAuthUrls = BitAuthUrls()) :
        this(client, cookieJar, null, urls)

    internal val auth = BitAuthSession(client, authUrls)
    val networkMode: NetworkMode get() = openedMode ?: error("BitApiClient: session not open")
    private val sessionMutex = Mutex()

    suspend fun acquireSession() = sessionMutex.lock()
    fun releaseSession() = sessionMutex.unlock()
    suspend fun prepareAccount(username: String) { browserSessions?.prepareAccount(username) }
    suspend fun clearBrowserSession() { browserSessions?.clear() }
    fun browserLoginUrl(): String = auth.urls.browserLoginUrl()

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val converterFactory =
        json.asConverterFactory("application/json".toMediaType())

    private var jwappRetrofit: Retrofit? = null
    private var jwmsRetrofit: Retrofit? = null
    private var lexueRetrofit: Retrofit? = null
    private var eclassRetrofit: Retrofit? = null
    private var openedMode: NetworkMode? = null

    /** Opens a session at the given [mode]. Idempotent if same mode re-requested. */
    fun open(mode: NetworkMode) {
        if (mode == openedMode) return
        val hosts = BitUrlsConfig.hostsFor(mode)
        jwappRetrofit = newRetrofit(hosts.jwapp)
        jwmsRetrofit = newRetrofit(hosts.jwms)
        lexueRetrofit = newRetrofit(hosts.lexue)
        eclassRetrofit = newRetrofit(hosts.eclass)
        openedMode = mode
    }

    /** Releases service instances and temporary native cookies. Browser cookies
     * remain until expiry, account switch or logout. */
    fun close() {
        jwappRetrofit = null
        jwmsRetrofit = null
        lexueRetrofit = null
        eclassRetrofit = null
        openedMode = null
        cookieJar.clear()
    }

    val jwapp: BitJwappService
        get() = jwappRetrofit?.create() ?: error("BitApiClient: session not open")

    /** 正方教务 (成绩 HTML) 服务，独立 host jwms.bit.edu.cn。 */
    val jwms: com.example.personal_studio.data.network.bit.service.BitJwmsService
        get() = jwmsRetrofit?.create() ?: error("BitApiClient: session not open")

    /** 乐学(Moodle)服务,独立 host lexue.bit.edu.cn。 */
    val lexue: com.example.personal_studio.data.network.bit.service.BitLexueService
        get() = lexueRetrofit?.create() ?: error("BitApiClient: session not open")

    /** 新乐学(畅课)服务,host zy-eclass.bit.edu.cn,校内外同一地址。 */
    val eclass: com.example.personal_studio.data.network.bit.service.BitEclassService
        get() = eclassRetrofit?.create() ?: error("BitApiClient: session not open")

    private fun newRetrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(converterFactory)
        .build()
}

/** Foreground imports and background workers share one cookie store and must not
 * close or switch the network mode of each other's in-flight requests. */
suspend inline fun <T> BitApiClient.withExclusiveSession(block: () -> T): T {
    acquireSession()
    try {
        return block()
    } finally {
        try { close() } finally { releaseSession() }
    }
}
