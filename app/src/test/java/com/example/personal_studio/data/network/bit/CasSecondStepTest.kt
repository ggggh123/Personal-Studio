package com.example.personal_studio.data.network.bit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Random
import javax.crypto.Cipher

/** The pieces of CAS's SMS / captcha API that do not need a server. */
class CasSecondStepTest {
    private fun obj(text: String) = Json.parseToJsonElement(text) as JsonObject

    @Test fun `csrf value is the MD5 of the key's Base64 wrapped in its halves`() {
        val headers = CasCrypto.csrfHeaders(Random(7))
        val key = headers.getValue("Csrf-Key")
        assertEquals(32, key.length)
        assertTrue(key.all { it.isLetterOrDigit() })
        val b64 = Base64.getEncoder().encodeToString(key.toByteArray())
        val mixed = b64.substring(0, b64.length / 2) + b64 + b64.substring(b64.length / 2)
        val md5 = MessageDigest.getInstance("MD5").digest(mixed.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(md5, headers.getValue("Csrf-Value"))
    }

    @Test fun `AES round trip and RSA that only the private key opens`() {
        val key = CasCrypto.newAesKey(Random(1))
        val sealed = CasCrypto.aesEncrypt("""{"userId":"obj-9"}""", key)
        assertEquals("""{"userId":"obj-9"}""", CasCrypto.aesDecrypt(sealed, key))

        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val text = CasCrypto.base64(key)
        val wrapped = CasCrypto.rsaEncrypt(text, Base64.getEncoder().encodeToString(pair.public.encoded))
        val opened = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.DECRYPT_MODE, pair.private) }
            .doFinal(Base64.getDecoder().decode(wrapped))
        assertEquals(text, String(opened))
    }

    @Test fun `the school's public key is a 2048-bit RSA key`() {
        val key = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(CasCrypto.URL_CRYPTO_PUBLIC_KEY))) as RSAPublicKey
        assertEquals(2048, key.modulus.bitLength())
    }

    @Test fun `encrypted paths match below cas or gate, with one-segment wildcards`() {
        assertTrue(CasApi.matches("/cas/api/protected/sms/getPhoneNumberByUserId", "/api/protected/sms/getPhoneNumberByUserId"))
        assertTrue(CasApi.matches("/cas/api/trusted/device/list/42", "/cas/api/trusted/device/list/xxx"))
        assertTrue(CasApi.matches("/gate/linkid/x/findTelByUserId/7", "/linkid/x/findTelByUserId/*"))
        assertFalse(CasApi.matches("/cas/api/protected/sms/checkToken", "/api/protected/sms/getPhoneNumberByUserId"))
        assertFalse(CasApi.matches("/cas/api/trusted/device/list", "/cas/api/trusted/device/list/xxx"))
    }

    @Test fun `replies give their code and a readable message`() {
        assertEquals(400, CasApi.code(obj("""{"code":400,"message":"验证码错误"}""")))
        assertEquals("验证码错误", CasApi.message(obj("""{"code":400,"message":"验证码错误"}""")))
        assertEquals("", CasApi.message(obj("""{"code":200,"message":"OK"}""")))
        assertEquals("发送太频繁", CasApi.message(obj("""{"code":412,"data":{"errorMessage":"发送太频繁"}}""")))
        assertEquals("", CasApi.message(obj("""{"code":412,"data":{"errorMessage":"{\"remaining\":30}"}}""")))
        assertNull(CasApi.parse("<html>login</html>"))
        assertTrue(CasApi.truthy(obj("""{"a":true}""")["a"]))
        assertTrue(CasApi.truthy(obj("""{"a":"true"}""")["a"]))
        assertFalse(CasApi.truthy(obj("""{"a":false}""")["a"]))
    }

    @Test fun `captcha images come as data URLs`() {
        assertArrayEquals(byteArrayOf(1, 2, 3), CasApi.dataUrlBytes("data:image/png;base64,AQID"))
        assertNull(CasApi.dataUrlBytes("https://sso.bit.edu.cn/captcha.png"))
        assertNull(CasApi.dataUrlBytes("data:image/png,raw"))
    }

    @Test fun `second step page is told apart from the ordinary login page`() {
        val url = "https://sso.bit.edu.cn/cas/login?service=https%3A%2F%2Fexample.bit.edu.cn%2F".toHttpUrl()
        val page = BitAuthPages.secondFactorPage(
            """<div id="login-page-flowkey">flow2</div><div id="user-object-id">obj-9</div>
               <div id="phone-number"></div><form id="secondSmsLoginForm" method="post" action="login?service=x&amp;y=1"></form>""",
            url,
        )!!
        assertEquals("flow2", page.execution)
        assertEquals("obj-9", page.userObjectId)
        assertEquals("", page.phone)
        assertEquals("/cas/login", page.formAction.encodedPath)
        assertEquals("x", page.formAction.queryParameter("service"))
        assertEquals("1", page.formAction.queryParameter("y"))
        // The ordinary page carries the same ids, empty.
        assertNull(BitAuthPages.secondFactorPage(
            """<div id="login-page-flowkey">flow1</div><div id="user-object-id"></div><div class="cas-gateway"></div>""", url))
        assertNull(BitAuthPages.secondFactorPage("""<form id='secondSmsLoginForm'>短信验证</form>""", url))
    }
}
