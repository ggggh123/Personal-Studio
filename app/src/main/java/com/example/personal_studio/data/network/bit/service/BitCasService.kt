package com.example.personal_studio.data.network.bit.service

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * BIT 统一身份认证 (CAS) endpoints. Both GET (init) and POST (submit) target
 * the same path `/cas/login`. The init response is HTML; the post response is
 * either a 302 redirect to a `service` URL (success) or a 200 HTML page
 * containing an error class (failure). We expose them as `ResponseBody` so
 * the caller can inspect both the status code and the body.
 */
interface BitCasService {

    @GET("cas/login")
    suspend fun getInitLogin(): Response<ResponseBody>

    @FormUrlEncoded
    @POST("cas/login")
    suspend fun postLogin(
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
