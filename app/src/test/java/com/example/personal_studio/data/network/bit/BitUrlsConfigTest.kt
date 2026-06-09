package com.example.personal_studio.data.network.bit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the off-campus 成绩查询 fix: the jwms CAS service-ticket activation must
 * traverse the webvpn gateway in WEBVPN mode, while LOCAL stays direct-sso
 * (byte-for-byte unchanged from the historical, working behaviour).
 */
class BitUrlsConfigTest {

    @Test fun `LOCAL cas-login endpoint is direct sso, unchanged`() {
        assertEquals(
            "https://sso.bit.edu.cn/cas/login",
            BitUrlsConfig.casLoginEndpoint(NetworkMode.LOCAL),
        )
    }

    @Test fun `WEBVPN cas-login endpoint routes through the webvpn gateway, not direct sso`() {
        val url = BitUrlsConfig.casLoginEndpoint(NetworkMode.WEBVPN)
        // The whole point of the fix: off campus the CAS activation goes through
        // webvpn.bit.edu.cn, NOT straight to sso.bit.edu.cn.
        assertTrue("expected webvpn host, got $url", url.startsWith("https://webvpn.bit.edu.cn/https/"))
        assertTrue("expected to hit /cas/login, got $url", url.endsWith("/cas/login"))
        // The encoded host segment must be present (decodes to sso.bit.edu.cn).
        assertTrue(url.contains("77726476706e69737468656265737421e3e44ed225397c1e7b0c9ce29b5b"))
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
