package com.example.personal_studio.data.network.bit.service

import com.example.personal_studio.data.network.bit.dto.GradeListResponse
import com.example.personal_studio.data.network.bit.dto.GradeRankResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * BIT 教务"成绩查询"(cjcx) ehall app。与 wdkbby 同 host(jxzxehallapp)、同 cookie。
 * 每个 ehall app 需各自 warm-up（getIndex + getAppConfig(本 app 的 appId) + i18n），
 * 否则 module 端点返回 403 openresty。
 *
 * ⚠ APP_ID 与各 .do 路径、字段为假设 —— 真机 DoD(Task 23) 验证修正。
 */
interface BitCjcxService {

    @GET("jwapp/sys/cjcx/*default/index.do")
    suspend fun getIndex(): Response<ResponseBody>

    @GET("jwapp/sys/funauthapp/api/getAppConfig/cjcx-{appId}.do")
    suspend fun getAppConfig(@Path("appId") appId: String = APP_ID): Response<ResponseBody>

    @GET("jwapp/i18n.do")
    suspend fun switchLang(
        @Query("appName") appName: String = "cjcx",
        @Query("EMAP_LANG") emapLang: String = "zh",
    ): Response<ResponseBody>

    /** 第一步：成绩列表。ehall queryList 风格表单参数；全量(不分页或大页)。 */
    @FormUrlEncoded
    @POST("jwapp/sys/cjcx/modules/cjcx/cxstuxqcj.do")
    suspend fun getGrades(
        @Field("querySetting") querySetting: String = "[]",
        @Field("pageSize") pageSize: Int = 1000,
        @Field("pageNumber") pageNumber: Int = 1,
        @Field("*order") order: String = "-XNXQDM",
    ): Response<GradeListResponse>

    /** 第二步：某学期班级/专业排名详情（对应"获取详细信息"）。 */
    @FormUrlEncoded
    @POST("jwapp/sys/cjcx/modules/cjcx/cxstupm.do")
    suspend fun getRankDetail(
        @Field("requestParamStr") requestParamStr: String,
    ): Response<GradeRankResponse>

    companion object {
        /** TBD —— 真机抓包确认 cjcx 的真实 appId。 */
        const val APP_ID = "4585275880135870"
    }
}
