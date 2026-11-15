package com.example.personal_studio.data.network.bit

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The original service URL is given to CAS; only the issued callback is routed through WebVPN. */
internal data class BitAuthUrls(
    val casLogin: String = "https://sso.bit.edu.cn/cas/login",
    val gatewayCallback: String = "https://webvpn.bit.edu.cn/login?cas_login=true",
    val jwappAuth: String = "https://jxzxehall.bit.edu.cn/auth-protocol-core/login?service=" +
        "https%3A%2F%2Fjxzxehallapp.bit.edu.cn%2Fjwapp%2Fsys%2Fwdkbby%2F*default%2Findex.do",
    val jwappLanding: String = "https://jxzxehallapp.bit.edu.cn/jwapp/sys/wdkbby/*default/index.do",
    val jwms: String = "http://jwms.bit.edu.cn/",
    val lexue: String = "https://lexue.bit.edu.cn/login/index.php",
    val proxyBases: Map<String, String> = mapOf(
        "https://jxzxehall.bit.edu.cn" to "https://webvpn.bit.edu.cn/https/77726476706e69737468656265737421faef5b842238695c72468ba58c1b26316e8e7f6f/",
        "https://jxzxehallapp.bit.edu.cn" to BitUrlsConfig.WEBVPN.jwapp,
        "http://jwms.bit.edu.cn" to BitUrlsConfig.WEBVPN.jwms,
        "https://jwms.bit.edu.cn" to BitUrlsConfig.WEBVPN.jwms.replace("/http/", "/https/"),
    ),
) {
    fun route(url: HttpUrl, mode: NetworkMode): HttpUrl {
        val proxy = if (mode == NetworkMode.WEBVPN) proxyBases[url.origin()] else null
        if (proxy == null) return url
        return (proxy + url.encodedPath.removePrefix("/")).toHttpUrl().newBuilder()
            .encodedQuery(url.encodedQuery).encodedFragment(url.encodedFragment).build()
    }

    fun trusted(url: HttpUrl): Boolean = url.origin() in
        (proxyBases.keys + proxyBases.values.map { it.toHttpUrl().origin() } +
            listOf(casLogin, gatewayCallback, jwappAuth, jwappLanding, jwms, lexue).map { it.toHttpUrl().origin() })

    fun isCas(url: HttpUrl): Boolean {
        val cas = casLogin.toHttpUrl()
        return url.origin() == cas.origin() && url.encodedPath.trimEnd('/') == cas.encodedPath.trimEnd('/')
    }

    fun browserLoginUrl(): String = casLogin.toHttpUrl().newBuilder()
        .addQueryParameter("service", gatewayCallback).build().toString()

    /** This is only a signal to retry native verification, never proof of login. */
    fun isBrowserCompletionCandidate(url: HttpUrl): Boolean =
        url.isHttps && url.origin() == gatewayCallback.toHttpUrl().origin() &&
            url.encodedPath == "/" && url.encodedQuery == null && url.encodedFragment == null

    fun browserRecoveryUrl(failed: HttpUrl, status: Int): String? {
        val cas = casLogin.toHttpUrl()
        val casPath = Regex.escape(cas.encodedPath.substringBeforeLast('/'))
        // The school's WebVPN post-login relay can be resolved relative to CAS
        // after SMS succeeds. Its cookie is already established; reopening the
        // gateway restores the portal. Never replay the token POST or assume login.
        val relayPath = Regex("$casPath/77726476706e69737468656265737421[0-9a-f]+/wengine-vpn-token-login/?")
        if (status != 404 || failed.origin() != cas.origin() || !relayPath.matches(failed.encodedPath)) return null
        return gatewayCallback.toHttpUrl().newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
    }
}

internal fun HttpUrl.origin(): String = newBuilder().encodedPath("/").query(null).fragment(null)
    .build().toString().removeSuffix("/")

internal data class BitAuthResponse(val code: Int, val url: HttpUrl, val location: String?, val body: String) {
    fun redirect(): HttpUrl? = location?.let { url.resolve(it) }
    fun diagnostic(stage: String): String = "$stage：HTTP $code，${url.host}${url.encodedPath}"
}

/** No implicit redirects: a CAS ticket must not be consumed on an unreachable campus host. */
internal class BitAuthSession(client: OkHttpClient, val urls: BitAuthUrls) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val publicClient = this.client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()

    suspend fun request(url: HttpUrl, fields: Map<String, String>? = null, referer: HttpUrl? = null, publicPreflight: Boolean = false): BitAuthResponse {
        require(urls.trusted(url)) { "认证跳转到了未知站点" }
        val builder = Request.Builder().url(url)
            .header("User-Agent", BitBrowserHeaders.USER_AGENT)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        referer?.let { builder.header("Referer", it.toString()) }
        if (publicPreflight) builder.tag(BitPublicPreflight::class.java, BitPublicPreflight)
        if (fields != null) {
            require(urls.isCas(url)) { "认证表单只能提交到学校统一身份认证站点" }
            builder.header("Origin", url.origin())
            builder.post(FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build())
        }
        return suspendCancellableCoroutine { continuation ->
            val call = (if (publicPreflight) publicClient else client).newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val value = response.use {
                            BitAuthResponse(it.code, it.request.url, it.header("Location"), it.body?.string().orEmpty())
                        }
                        if (continuation.isActive) continuation.resume(value)
                    } catch (e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
    }
}

object BitBrowserHeaders {
    // Shared by the native session and the verification WebView, so SSO sees the same browser.
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36"
}
