package com.example.personal_studio.data.network.bit

/**
 * Base URLs for BIT 统一身份认证 + 教务系统 endpoints.
 *
 * CAS (SSO) and jwapp (教务系统) live on **different hosts** — verified via
 * real-device DoD logcat 2026-05. The first iteration assumed a single host
 * per session and got 500s from nginx on every jwapp call.
 *
 * Host map (matching BIT101 reference's PROD_URLS):
 *
 * ```
 *                  CAS (sso)              jwapp (jxzxehallapp)
 * LOCAL    sso.bit.edu.cn          jxzxehallapp.bit.edu.cn
 * WEBVPN   sso.bit.edu.cn          webvpn.bit.edu.cn/https/<encoded prefix>/
 * ```
 *
 * WebVPN encodes the destination host into a path prefix (BIT's gateway
 * decodes + proxies). Requests to /jwapp/... go through the encoded prefix
 * transparently when we set the WebVPN base URL to include that prefix.
 *
 * The single OkHttpClient + shared cookie jar handle session continuity
 * across hosts (BIT sets `.bit.edu.cn` parent-domain cookies; webvpn keeps
 * its own session cookie at webvpn.bit.edu.cn).
 */
object BitUrlsConfig {

    data class Hosts(val jwapp: String, val jwms: String, val lexue: String, val eclass: String = ECLASS)

    /** 新乐学 (畅课) is public: the same host on and off campus. */
    const val ECLASS = "https://zy-eclass.bit.edu.cn/"

    /** Campus data endpoints. jwms's registered service uses HTTP; off campus
     * requests are carried inside the HTTPS WebVPN connection below. */
    val LOCAL = Hosts(
        jwapp = "https://jxzxehallapp.bit.edu.cn/",
        jwms  = "http://jwms.bit.edu.cn/",
        lexue = "https://lexue.bit.edu.cn/",
    )

    /** Off-campus endpoints, matching BIT-Login v4.0.2's gateway host mapping.
     * CAS remains direct; BitAuthUrls rewrites each issued service callback. */
    val WEBVPN = Hosts(
        jwapp = "https://webvpn.bit.edu.cn/https/" +
            "77726476706e69737468656265737421faef5b842238695c720999bcd6572a216b231105adc27d/",
        jwms  = "https://webvpn.bit.edu.cn/http/" +
            "77726476706e69737468656265737421fae04c8f69326144300d8db9d6562d/",
        lexue = "https://lexue.bit.edu.cn/",
    )

    fun hostsFor(mode: NetworkMode): Hosts = when (mode) {
        NetworkMode.LOCAL  -> LOCAL
        NetworkMode.WEBVPN -> WEBVPN
    }

}
