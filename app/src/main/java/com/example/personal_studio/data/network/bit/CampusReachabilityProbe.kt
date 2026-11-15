package com.example.personal_studio.data.network.bit

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Anonymous direct-access probe. The teaching portal is publicly reachable now,
 * so reaching it (or following its redirect to public SSO) cannot identify campus
 * access. Probe jwms without redirects/cookies and reject HTTP error pages.
 */
@Singleton
class CampusReachabilityProbe internal constructor(
    private val client: OkHttpClient,
    private val url: String,
) {
    @Inject constructor() : this(OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(), LOCAL_PROBE_URL)

    /** Direct-route availability only; cancel the network request when the caller leaves. */
    suspend fun localReachable(): Boolean = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).head().build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(false)
            }

            override fun onResponse(call: Call, response: Response) {
                val reachable = response.use { it.isSuccessful }
                if (continuation.isActive) continuation.resume(reachable)
            }
        })
    }

    companion object {
        const val LOCAL_PROBE_URL = "http://jwms.bit.edu.cn/"
    }
}
