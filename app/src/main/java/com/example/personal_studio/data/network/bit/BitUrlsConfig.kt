package com.example.personal_studio.data.network.bit

/**
 * Base URLs for BIT 统一身份认证 + 教务系统 endpoints.
 *
 * Both LOCAL and WEBVPN modes use the *same* path strings on their Retrofit
 * interfaces (e.g., `/cas/login`, `/jwapp/...`); only the host differs. BIT's
 * WebVPN gateway performs host-only rewriting, not path-prefix wrapping. If
 * real-device testing reveals path-prefix wrapping IS required, a
 * `WebVpnPathInterceptor` will be added as a targeted patch.
 *
 * **Important — host history.** The historical SSO host `login.bit.edu.cn`
 * currently 302-redirects to `sso.bit.edu.cn`. OkHttp follows redirects on GET
 * transparently, but on POST a 302 is *downgraded to GET per HTTP spec*, which
 * silently drops our credentials. So we MUST POST directly to `sso.bit.edu.cn`
 * without going through the redirect. (Verified 2026-05 via WebFetch.)
 */
object BitUrlsConfig {
    const val LOCAL_BASE  = "https://sso.bit.edu.cn/"
    const val WEBVPN_BASE = "https://webvpn.bit.edu.cn/"

    fun baseFor(mode: NetworkMode): String = when (mode) {
        NetworkMode.LOCAL  -> LOCAL_BASE
        NetworkMode.WEBVPN -> WEBVPN_BASE
    }
}
