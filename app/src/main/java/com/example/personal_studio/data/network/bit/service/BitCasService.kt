package com.example.personal_studio.data.network.bit.service

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * BIT 统一身份认证 (CAS) endpoints.
 *
 * CAS is **service-bound**: you cannot just POST credentials to `/cas/login`
 * and get back a usable session — BIT's server returns 401. You must POST to
 * the EXACT URL that the redirect chain landed you at, which includes a
 * `service=<protected-resource-url>?sessionToken=<nonce>` query param. The
 * orchestrator triggers the redirect by GETing the protected resource and
 * captures the final URL via `Response.raw().request.url.toString()`, then
 * calls [postLoginAt] with that URL.
 *
 * Init-page GET ([getInitLogin]) is still used by some test cases — it returns
 * the bare login form HTML. It's NOT used in production because POSTing to
 * the bare `/cas/login` (no service param) gets 401.
 */
interface BitCasService {

    @GET("cas/login")
    suspend fun getInitLogin(): Response<ResponseBody>

    /** POSTs the encrypted CAS login form to the specific URL captured from
     *  the protected-resource redirect — typically of the shape
     *  `https://sso.bit.edu.cn/cas/login?service=<URL-encoded-callback>`. */
    @FormUrlEncoded
    @POST
    suspend fun postLoginAt(
        @Url url: String,
        @Field("username") username: String,
        @Field("password") encryptedPassword: String,
        @Field("execution") execution: String,
        @Field("croypto") salt: String,
        @Field("captcha_payload") captchaPayload: String = "",
        @Field("captcha_code") captchaCode: String = "",
        @Field("type") type: String = "UsernamePassword",
        @Field("geolocation") geolocation: String = "",
        @Field("_eventId") eventId: String = "submit",
    ): Response<ResponseBody>

    /**
     * Service-ticket exchange. After a full password login, CAS holds a
     * ticket-granting cookie (TGC) on sso.bit.edu.cn. GETting
     * `cas/login?service=<other service>` with that TGC makes CAS issue a
     * service ticket and 302 to `<service>?ticket=ST-...`; OkHttp follows it,
     * the target validates the ticket and sets its own session cookie.
     *
     * Used to activate the 正方教务 (jwms) session after the initial login —
     * jwms registered its service as `http://jwms.bit.edu.cn/`.
     */
    @GET("cas/login")
    suspend fun activateService(@Query("service") service: String): Response<ResponseBody>

    /**
     * Same service-ticket exchange as [activateService], but with the CAS-login
     * endpoint supplied as a full [Url] instead of resolved against the cas
     * base host.
     *
     * Needed for **webvpn-proxied** targets (jwms off-campus): the CAS endpoint
     * itself must be reached THROUGH the webvpn gateway, so the gateway can
     * intercept CAS's internal `302 → http://jwms.bit.edu.cn/?ticket=…` bounce,
     * follow it inside the proxy, and scope the resulting jsxsd session cookie to
     * `webvpn.bit.edu.cn` — which is where the subsequent score fetch goes. If we
     * hit the direct `sso.bit.edu.cn` host instead (as [activateService] does),
     * the bounce escapes the gateway: off campus the raw host is unreachable, and
     * on campus the cookie lands on the wrong host so the webvpn fetch is
     * unauthenticated. (Mirrors BIT101-GO pkg/webvpn/score.go `pre_url`.)
     *
     * [service] stays the raw target root (e.g. `http://jwms.bit.edu.cn/`) and is
     * query-encoded by Retrofit.
     */
    @GET
    suspend fun activateServiceAt(
        @Url casLoginUrl: String,
        @Query("service") service: String,
    ): Response<ResponseBody>
}
