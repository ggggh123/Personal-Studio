package com.example.personal_studio.data.network.bit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class BitSessionConcurrencyTest {
    @Test fun `cancelled waiter cannot clear another sync cookie jar or switch its mode`() = runTest {
        val jar = BitCookieJar()
        val api = BitApiClient(OkHttpClient.Builder().cookieJar(jar).build(), jar)
        val url = "https://webvpn.bit.edu.cn/".toHttpUrl()
        val release = CompletableDeferred<Unit>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            api.withExclusiveSession {
                api.open(NetworkMode.WEBVPN)
                jar.saveFromResponse(url, listOf(Cookie.Builder().name("SESSION").value("first").domain(url.host).build()))
                release.await()
            }
        }
        var entered = false
        val waiting = launch(start = CoroutineStart.UNDISPATCHED) {
            api.withExclusiveSession { entered = true; api.open(NetworkMode.LOCAL) }
        }
        waiting.cancelAndJoin()
        assertFalse(entered)
        assertEquals(NetworkMode.WEBVPN, api.networkMode)
        assertEquals("first", jar.loadForRequest(url).single().value)
        release.complete(Unit)
        first.join()
        assertTrue(jar.loadForRequest(url).isEmpty())
        api.withExclusiveSession { api.open(NetworkMode.LOCAL); assertEquals(NetworkMode.LOCAL, api.networkMode) }
    }

    @Test fun `cancelled owner releases session for next sync`() = runTest {
        val api = BitApiClient(OkHttpClient(), BitCookieJar())
        val owner = launch(start = CoroutineStart.UNDISPATCHED) {
            api.withExclusiveSession { api.open(NetworkMode.LOCAL); CompletableDeferred<Unit>().await() }
        }
        owner.cancelAndJoin()
        api.withExclusiveSession { api.open(NetworkMode.WEBVPN); assertEquals(NetworkMode.WEBVPN, api.networkMode) }
    }
}
