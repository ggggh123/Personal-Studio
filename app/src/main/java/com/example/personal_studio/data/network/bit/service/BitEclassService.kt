package com.example.personal_studio.data.network.bit.service

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 新乐学(畅课 TronClass, zy-eclass.bit.edu.cn)只读接口。base url 末尾带斜杠。
 * 未登录时这些地址会跳去统一认证并最终返回 HTML,所以一律取原始响应体,由调用方判断是不是 JSON。
 */
interface BitEclassService {
    /** 我的学期;`is_active` 标出当前学期。 */
    @GET("api/my-semesters")
    suspend fun semesters(): Response<ResponseBody>

    /** 我的课程(POST 查询):`{"page","page_size","fields","conditions":{"semester_id":[…]}}`。 */
    @POST("api/my-courses")
    suspend fun courses(@Body query: RequestBody): Response<ResponseBody>

    /** 一门课的作业(含课堂小测),带截止时间和本人是否已提交。 */
    @GET("api/courses/{courseId}/homework-activities")
    suspend fun homework(
        @Path("courseId") courseId: Long,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): Response<ResponseBody>
}
