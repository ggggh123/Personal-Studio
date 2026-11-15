package com.example.personal_studio.data.network.bit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * CAS stays direct; only protected service callbacks are routed through WebVPN.
 */
class BitUrlsConfigTest {

    @Test fun `only clean HTTPS gateway home can automatically retry native verification`() {
        val urls = BitAuthUrls()
        assertTrue(urls.isBrowserCompletionCandidate("https://webvpn.bit.edu.cn/".toHttpUrl()))
        listOf(
            "https://sso.bit.edu.cn/cas/login",
            "https://webvpn.bit.edu.cn/login?cas_login=true",
            "https://webvpn.bit.edu.cn/?ticket=not-consumed",
            "https://webvpn.bit.edu.cn/#/login",
            "http://webvpn.bit.edu.cn/",
            "https://webvpn.bit.edu.cn.evil.invalid/",
        ).forEach { assertFalse(it, urls.isBrowserCompletionCandidate(it.toHttpUrl())) }
    }

    @Test fun `known CAS WebVPN relay 404 recovers at gateway without replaying token`() {
        val failed = "https://sso.bit.edu.cn/cas/77726476706e69737468656265737421fcf8469529/wengine-vpn-token-login?token=private".toHttpUrl()
        assertEquals("https://webvpn.bit.edu.cn/", BitAuthUrls().browserRecoveryUrl(failed, 404))
        assertNull(BitAuthUrls().browserRecoveryUrl(failed, 401))
        assertNull(BitAuthUrls().browserRecoveryUrl(failed.newBuilder().host("untrusted.invalid").build(), 404))
        assertNull(BitAuthUrls().browserRecoveryUrl("https://sso.bit.edu.cn/cas/unrelated".toHttpUrl(), 404))
    }

    @Test fun `CAS ticket issuance stays on direct SSO`() {
        assertEquals(
            "https://sso.bit.edu.cn/cas/login",
            BitAuthUrls().casLogin,
        )
    }

    @Test fun `only the callback changes route and its query remains byte exact`() {
        val urls = BitAuthUrls()
        val callback = "http://jwms.bit.edu.cn/?ticket=ST-123&nonce=a%2Bb%26c".toHttpUrl()
        val routed = urls.route(callback, NetworkMode.WEBVPN)
        assertEquals("webvpn.bit.edu.cn", routed.host)
        assertEquals(callback.encodedQuery, routed.encodedQuery)
        assertEquals(callback, urls.route(callback, NetworkMode.LOCAL))
        assertEquals(urls.casLogin, urls.route(urls.casLogin.toHttpUrl(), NetworkMode.WEBVPN).toString())
    }

    @Test fun `WEBVPN jwms base prefix is the BIT101-GO-verified encoding of jwms_bit_edu_cn`() {
        // Regression guard: this encoded prefix decodes to jwms.bit.edu.cn and is
        // identical to BIT101-GO pkg/webvpn/score.go's score_url prefix.
        assertEquals(
            "https://webvpn.bit.edu.cn/http/" +
                "77726476706e69737468656265737421fae04c8f69326144300d8db9d6562d/",
            BitUrlsConfig.WEBVPN.jwms,
        )
    }
}
