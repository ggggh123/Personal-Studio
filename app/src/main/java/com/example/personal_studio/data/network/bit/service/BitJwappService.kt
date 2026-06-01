package com.example.personal_studio.data.network.bit.service

import com.example.personal_studio.data.network.bit.dto.BuildingResponse
import com.example.personal_studio.data.network.bit.dto.CampusResponse
import com.example.personal_studio.data.network.bit.dto.ExamResponse
import com.example.personal_studio.data.network.bit.dto.RoomOccupancyResponse
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

    // ── ehall studentWdksapApp (考试安排) — same host/session, separate app warm-up ──

    @GET("jwapp/sys/funauthapp/api/getAppConfig/studentWdksapApp-{appId}.do")
    suspend fun getExamAppConfig(
        @Path("appId") appId: String = "4768687067472349",
    ): Response<ResponseBody>

    @GET("jwapp/i18n.do")
    suspend fun switchLangExam(
        @Query("appName") appName: String = "studentWdksapApp",
        @Query("EMAP_LANG") emapLang: String = "zh",
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("jwapp/sys/studentWdksapApp/WdksapController/cxxsksap.do")
    suspend fun getExamSchedule(
        @Field("requestParamStr") requestParamStr: String,
    ): Response<ExamResponse>

    // ── ehall kxjasbyMobile (空教室) — 同 host/session,复用 wdkbby warm-up ──

    @GET("jwapp/sys/kxjasbyMobile/modules/jxllb/ggzdpx.do")
    suspend fun getCampuses(
        @Query("dicCode") dicCode: String = "48682",
        @Query("SFSY") sfsy: String = "1",
        @Query("order") order: String = "+DM",
    ): Response<CampusResponse>

    @GET("jwapp/sys/kxjasbyMobile/modules/jxllb/cxjxl.do")
    suspend fun getBuildings(
        @Query("XXXQDM") campusCode: String? = null,
    ): Response<BuildingResponse>

    @FormUrlEncoded
    @POST("jwapp/sys/kxjasbyMobile/kxjasbyController/cxkxjasqk.do")
    suspend fun getRoomOccupancy(
        @Field("XQDM") xqdm: String,
        @Field("JXLDM") jxldm: String,
        @Field("RQ") rq: String,
        @Field("XNXQDM") xnxqdm: String,
        @Field("XNDM") xndm: String,
    ): Response<RoomOccupancyResponse>
}
