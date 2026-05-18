package com.example.personal_studio.data.network.bit

/**
 * Base URLs for BIT 统一身份认证 + 教务系统 endpoints.
 *
 * Both LOCAL and WEBVPN modes use the *same* path strings on their Retrofit
 * interfaces (e.g., `/cas/login`, `/jwapp/...`); only the host differs. BIT's
 * WebVPN gateway performs host-only rewriting, not path-prefix wrapping. If
 * real-device testing in `p5-polish` reveals path-prefix wrapping IS required,
 * a `WebVpnPathInterceptor` will be added as a targeted patch.
 */
object BitUrlsConfig {
    const val LOCAL_BASE  = "https://login.bit.edu.cn/"
    const val WEBVPN_BASE = "https://webvpn.bit.edu.cn/"

    fun baseFor(mode: NetworkMode): String = when (mode) {
        NetworkMode.LOCAL  -> LOCAL_BASE
        NetworkMode.WEBVPN -> WEBVPN_BASE
    }
}
