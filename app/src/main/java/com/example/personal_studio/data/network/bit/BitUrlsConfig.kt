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

    data class Hosts(val cas: String, val jwapp: String, val jwms: String, val lexue: String)

    /** Campus-network direct.
     *  `jwms` = 正方教务系统 (成绩查询 lives here as HTML, NOT in the ehall app).
     *  CAS registered jwms's service as http://jwms.bit.edu.cn/, but the host
     *  also serves https; we fetch over https to avoid cleartext (the CAS
     *  ticket bounce to http is permitted via network_security_config). */
    val LOCAL = Hosts(
        cas   = "https://sso.bit.edu.cn/",
        jwapp = "https://jxzxehallapp.bit.edu.cn/",
        jwms  = "http://jwms.bit.edu.cn/",
        lexue = "https://lexue.bit.edu.cn/",
    )

    /** Off-campus through BIT's WebVPN gateway. The /https/<encoded>/ prefix
     *  is BIT's URL-rewriting scheme — verified against BIT101-Android's
     *  DEFAULT_WEB_VPN_URLS reference table. The jwms encoded host comes from
     *  BIT101-GO's score module (the `/http/...` jsxsd prefix). WEBVPN grades
     *  are untested on real device (same pending status as P5 WebVPN). */
    val WEBVPN = Hosts(
        cas   = "https://sso.bit.edu.cn/",
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
