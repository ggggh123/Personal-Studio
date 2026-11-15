package com.example.personal_studio.data.network.bit

import android.content.Context
import android.webkit.CookieManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** App-private browser cookies let native sync reuse an official SMS verification. */
@Singleton
class BitBrowserSessions @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("bit_browser_session", Context.MODE_PRIVATE)
    @Volatile private var manager: CookieManager? = null

    suspend fun prepareAccount(username: String) = withContext(Dispatchers.Main.immediate) {
        val cookies = manager ?: CookieManager.getInstance().also { manager = it }
        cookies.setAcceptCookie(true)
        if (prefs.getString("owner", null) != username) {
            clearCookies(cookies)
            prefs.edit().putString("owner", username).apply()
        }
    }

    suspend fun clear() = withContext(Dispatchers.Main.immediate) {
        clearCookies(manager ?: CookieManager.getInstance().also { manager = it })
        prefs.edit().clear().apply()
    }

    private suspend fun clearCookies(cookies: CookieManager) {
        suspendCancellableCoroutine { continuation ->
            cookies.removeAllCookies { if (continuation.isActive) continuation.resume(Unit) }
        }
        cookies.flush()
    }

    // CookieManager does domain/path/expiry matching itself. Re-parsing its header into
    // OkHttp Cookies would discard those scopes, especially WebVPN's nested paths.
    fun cookiesFor(url: HttpUrl): String? = if (isSchoolHost(url)) {
        manager?.getCookie(url.toString()) ?: manager?.let { "" }
    } else null

    fun save(url: HttpUrl, values: List<String>) {
        if (!isSchoolHost(url)) return
        val cookies = manager ?: return
        // This overload is synchronous and safe on the OkHttp thread. Redirects must
        // see Set-Cookie immediately, before the next request in the chain.
        values.forEach { cookies.setCookie(url.toString(), it) }
    }

    companion object {
        fun isSchoolHost(url: HttpUrl): Boolean = url.host == "bit.edu.cn" || url.host.endsWith(".bit.edu.cn")
    }
}

internal object BitPublicPreflight

class BitBrowserCookieInterceptor @Inject constructor(
    private val sessions: BitBrowserSessions,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val publicPreflight = original.tag(BitPublicPreflight::class.java) != null
        val request = original.newBuilder().apply {
            if (publicPreflight) removeHeader("Cookie")
            else sessions.cookiesFor(original.url)?.let {
                if (it.isEmpty()) removeHeader("Cookie") else header("Cookie", it)
            }
        }.build()
        return chain.proceed(request).also { response ->
            if (!publicPreflight) sessions.save(response.request.url, response.headers("Set-Cookie"))
        }
    }
}
