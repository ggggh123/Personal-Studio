package com.example.personal_studio.data.network.bit

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cookie jar scoped to a single BIT session. Cleared explicitly on
 * [clear] (called by `BitApiClient.close()`) — cookies never survive a wizard
 * dismissal, which limits the value of a stolen session.
 *
 * Implementation is conservative: we replace any prior cookies for a host on
 * each response. BIT serves multiple subdomains under bit.edu.cn (the CAS host
 * sets a parent-domain cookie that is then read by jwapp endpoints), so we key
 * by [Cookie.domain].
 */
@Singleton
class BitCookieJar @Inject constructor() : CookieJar {

    private val store = mutableMapOf<String, List<Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.groupBy { it.domain }.forEach { (domain, list) ->
            val existing = store[domain].orEmpty()
            val merged = (existing + list).distinctBy { it.name }
            store[domain] = merged
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        return store.entries
            .filter { (domain, _) -> host == domain || host.endsWith(".$domain") }
            .flatMap { it.value }
    }

    @Synchronized
    fun clear() {
        store.clear()
    }
}
