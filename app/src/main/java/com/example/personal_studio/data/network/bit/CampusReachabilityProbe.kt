package com.example.personal_studio.data.network.bit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 快速探测「此刻是否在校园网」:对**仅校园网可达**的 ehall host 做一次**短超时** HEAD。
 * 连得上(任何 HTTP 响应)= 校内;短超时内连不上(IOException)= 校外。
 *
 * 用独立的短超时 OkHttp(2s),不复用共享的 30s 客户端,也不带 cookie——纯连通性探测,
 * 不碰会话。用于「校内优先」:已记住 WEBVPN 时先探一次校内,避免在校园网下一直粘在校外慢路。
 */
@Singleton
class CampusReachabilityProbe @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .build()

    /** true = 校内 host 短超时内可达(在校园网);false = 不可达(校外)或探测出错。 */
    suspend fun localReachable(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(LOCAL_PROBE_URL).head().build())
                .execute().use { true }   // 拿到任何响应即视为连通(即便 4xx/5xx,也说明在校内)
        }.getOrDefault(false)
    }

    companion object {
        /** 仅校园网可直连的 ehall host(校外不可达,故可作「在不在校内」的判据)。 */
        const val LOCAL_PROBE_URL = "https://jxzxehallapp.bit.edu.cn/"
    }
}
