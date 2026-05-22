package com.example.personal_studio.data.network.bit.service

import com.example.personal_studio.data.network.bit.dto.ScheduleResponse
import com.example.personal_studio.data.network.bit.dto.TermListResponse
import com.example.personal_studio.data.network.bit.dto.WeekDateResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * BIT 教务系统 "wdkbby" (我的课表) app endpoints under the /jwapp prefix.
 *
 * Workflow: init session via /index.do + /getAppConfig + /i18n (these "warm-up"
 * calls are required by BIT to set session-scoped cookies); then fetch current
 * term, term list, current-week info, and finally the actual schedule rows.
 */
interface BitJwappService {

    @GET("jwapp/sys/wdkbby/*default/index.do")
    suspend fun getIndex(): Response<ResponseBody>

    @GET("jwapp/sys/funauthapp/api/getAppConfig/wdkbby-{appId}.do")
    suspend fun getAppConfig(
        @Path("appId") appId: String = "5959167891382285",
    ): Response<ResponseBody>

    @GET("jwapp/i18n.do")
    suspend fun switchLang(
        @Query("appName") appName: String = "wdkbby",
        @Query("EMAP_LANG") emapLang: String = "zh",
    ): Response<ResponseBody>

    @GET("jwapp/sys/wdkbby/modules/jshkcb/dqxnxq.do")
    suspend fun getCurrentTerm(): Response<TermListResponse>

    @GET("jwapp/sys/wdkbby/modules/jshkcb/xnxqcx.do")
    suspend fun getTerms(): Response<TermListResponse>

    @FormUrlEncoded
    @POST("jwapp/sys/wdkbby/wdkbByController/cxzkbrq.do")
    suspend fun getWeekAndDate(
        @Field("requestParamStr") requestParamStr: String,
    ): Response<WeekDateResponse>

    @FormUrlEncoded
    @POST("jwapp/sys/wdkbby/modules/xskcb/cxxszhxqkb.do")
    suspend fun getSchedule(
        @Field("XNXQDM") xnxqdm: String,
    ): Response<ScheduleResponse>
}
