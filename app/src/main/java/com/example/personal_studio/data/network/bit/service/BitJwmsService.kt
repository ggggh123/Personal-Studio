package com.example.personal_studio.data.network.bit.service

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET

/**
 * BIT 正方教务系统 (jsxsd) on jwms.bit.edu.cn. This is where 成绩 actually lives
 * — as server-rendered HTML tables, NOT JSON (the ehall cjcx app has no grade
 * permission, which is why it 403'd during DoD). Discovered via BIT101-GO's
 * webvpn/score.go reference.
 *
 * Responses are HTML; we return raw [ResponseBody] and parse with a small
 * regex/table parser (same no-jsoup approach as the CAS login page).
 */
interface BitJwmsService {

    /** 成绩查询列表 — HTML table with id `dataList`. Requires an active jwms
     *  session (see [BitCasService.activateService]). If the student hasn't
     *  finished 评教 (course evaluation), this page hides grades and contains
     *  the word "评教" instead. */
    @GET("jsxsd/kscj/cjcx_list")
    suspend fun getScoreListHtml(): Response<ResponseBody>
}
