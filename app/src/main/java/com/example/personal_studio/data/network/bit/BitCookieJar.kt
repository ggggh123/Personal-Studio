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
 * Stores cookies keyed by `(domain, name)` so a newer Set-Cookie for the same
 * (domain, name) pair fully replaces the older value (e.g. when CAS rotates
 * the SESSION cookie mid-redirect-chain).
 *
 * Filtering on load uses OkHttp's [Cookie.matches] — that's the canonical
 * implementation of cookie path / domain / Secure / hostOnly semantics. Doing
 * our own domain-prefix check would (and did, in the first iteration) miss
 * Path-scoped cookies and host-only vs domain cookies.
 */
@Singleton
class BitCookieJar @Inject constructor() : CookieJar {

    private data class Key(val domain: String, val name: String)

    private val store = mutableMapOf<Key, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            store[Key(cookie.domain, cookie.name)] = cookie
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        // Drop expired cookies eagerly while we're iterating.
        val now = System.currentTimeMillis()
        val expired = mutableListOf<Key>()
        val matched = mutableListOf<Cookie>()
        for ((key, cookie) in store) {
            if (cookie.expiresAt <= now) {
                expired += key
                continue
            }
            if (cookie.matches(url)) matched += cookie
        }
        for (k in expired) store.remove(k)
        return matched
    }

    /** Clears all cookies. Intended to be called only by [BitApiClient.close];
     *  do not invoke from elsewhere — would nuke an in-flight import session. */
    @Synchronized
    fun clear() {
        store.clear()
    }
}
