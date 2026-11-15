package com.example.personal_studio.data.network.bit

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class BitCookieJarTest {
    private val jar = BitCookieJar()
    private val origin = "https://webvpn.bit.edu.cn/".toHttpUrl()

    private fun cookie(path: String, value: String, expires: Long = Long.MAX_VALUE) = Cookie.Builder()
        .hostOnlyDomain(origin.host).name("SESSION").value(value).path(path).secure()
        .expiresAt(expires).build()

    @Test fun `gateway and service cookies with the same name retain their paths`() {
        jar.saveFromResponse(origin, listOf(cookie("/", "gateway"), cookie("/https/sso", "cas")))
        assertEquals(listOf("gateway"), jar.loadForRequest(origin).map { it.value })
        assertEquals(listOf("cas", "gateway"),
            jar.loadForRequest(origin.resolve("https/sso/cas/login")!!).map { it.value })
    }

    @Test fun `expiring one path does not delete the cookie for another service`() {
        jar.saveFromResponse(origin, listOf(cookie("/", "gateway"), cookie("/https/sso", "cas")))
        jar.saveFromResponse(origin, listOf(cookie("/https/sso", "deleted", 1)))
        assertEquals(listOf("gateway"), jar.loadForRequest(origin).map { it.value })
    }

    @Test fun `replacement and matching keep domain secure and expiry rules`() {
        jar.saveFromResponse(origin, listOf(cookie("/", "old")))
        jar.saveFromResponse(origin, listOf(cookie("/", "new")))
        assertEquals(listOf("new"), jar.loadForRequest(origin).map { it.value })
        assertEquals(emptyList<Cookie>(), jar.loadForRequest("http://webvpn.bit.edu.cn/".toHttpUrl()))
        assertEquals(emptyList<Cookie>(), jar.loadForRequest("https://sso.bit.edu.cn/".toHttpUrl()))
        jar.clear()
        assertEquals(emptyList<Cookie>(), jar.loadForRequest(origin))
    }
}
