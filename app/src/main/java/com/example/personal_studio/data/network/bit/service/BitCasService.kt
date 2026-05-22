package com.example.personal_studio.data.network.bit.service

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
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
}
