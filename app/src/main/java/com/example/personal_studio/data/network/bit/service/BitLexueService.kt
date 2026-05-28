package com.example.personal_studio.data.network.bit.service

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

/** 乐学(BIT Moodle, lexue.bit.edu.cn)端点。base url 末尾带斜杠。 */
interface BitLexueService {
    /** 乐学首页 HTML,用于正则抠 sesskey。 */
    @GET(".")
    suspend fun getIndexHtml(): Response<ResponseBody>

    /** 生成 iCal 订阅 URL;返回 HTML,含生成的 .ics URL。 */
    @FormUrlEncoded
    @POST("calendar/export.php")
    suspend fun exportCalendar(
        @Field("sesskey") sesskey: String,
        @Field("_qf__core_calendar_export_form") formMarker: String = "1",
        @Field("events[exportevents]") events: String = "all",
        @Field("period[timeperiod]") period: String = "recentupcoming",
        @Field("generateurl") generate: String = "获取日历网址",
    ): Response<ResponseBody>

    /** 直接 GET 持久化的 .ics 订阅 URL(authtoken 自带鉴权)。 */
    @GET
    suspend fun getIcs(@Url url: String): Response<ResponseBody>
}
