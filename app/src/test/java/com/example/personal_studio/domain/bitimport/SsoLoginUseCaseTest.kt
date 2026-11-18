package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.BitAuthUrls
import com.example.personal_studio.data.network.bit.BitBrowserHeaders
import com.example.personal_studio.data.network.bit.BitCookieJar
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.CasInitDto
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.origin
import com.example.personal_studio.core.util.CasAesCrypto
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Exercises the production login entry points and real redirects/cookies. Each
 * school host has its own server; no authenticated requests leave localhost. */
class SsoLoginUseCaseTest {
    private val cas = MockWebServer()
    private val vpn = MockWebServer()
    private val portal = MockWebServer()
    private val eclass = MockWebServer()
    private val idp = MockWebServer()
    private val jar = BitCookieJar()
    private val sso = SsoLoginUseCase()
    private lateinit var urls: BitAuthUrls
    private lateinit var api: BitApiClient
    private fun MockWebServer.at(host: String, path: String): HttpUrl = url(path).newBuilder().host(host).build()
    private fun casUrl(path: String) = cas.at("sso.test", path)
    private fun vpnUrl(path: String) = vpn.at("vpn.test", path)
    private fun portalUrl(path: String) = portal.at("portal.test", path)
    private fun eclassUrl(path: String) = eclass.at("eclass.test", path)
    private fun idpUrl(path: String) = idp.at("idp.test", path)
    private fun fixture() = javaClass.getResourceAsStream("/bit-fixtures/cas-login-page.html")!!
        .bufferedReader().use { it.readText() }
    private fun redirect(url: Any) = MockResponse().setResponseCode(302).setHeader("Location", url)
    private fun success() = MockResponse().setBody("<html>教学服务主页</html>")

    @Before fun setUp() {
        listOf(cas, vpn, portal, eclass, idp).forEach { it.start() }
        urls = BitAuthUrls(
            casLogin = casUrl("/cas/login").toString(),
            gatewayCallback = vpnUrl("/login?cas_login=true").toString(),
            jwappAuth = portalUrl("/auth?service=app").toString(),
            jwappLanding = portalUrl("/jwapp/sys/wdkbby/*default/index.do").toString(),
            jwms = portalUrl("/").toString(),
            lexue = portalUrl("/login/index.php").toString(),
            eclass = eclassUrl("/user/courses").toString(),
            eclassIdentity = idpUrl("/auth/realms/bit/").toString(),
            proxyBases = mapOf(portalUrl("/").origin() to vpnUrl("/https/campus/").toString()),
        )
        val client = OkHttpClient.Builder().cookieJar(jar)
            .dns(object : Dns {
                override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1"))
            })
            .callTimeout(5, TimeUnit.SECONDS).build()
        api = BitApiClient(client, jar, urls)
        api.open(NetworkMode.WEBVPN)
    }

    @After fun tearDown() {
        api.close()
        listOf(cas, vpn, portal, eclass, idp).forEach { it.shutdown() }
    }

    private fun passwordTicket(service: HttpUrl = portalUrl("/")) {
        cas.enqueue(MockResponse().setBody(fixture()).addHeader("Set-Cookie", "SESSION=cas; Path=/cas"))
        cas.enqueue(redirect(service.newBuilder().addQueryParameter("ticket", "ST-target").build())
            .addHeader("Set-Cookie", "TGC=trusted; Path=/cas"))
    }

    private fun gatewayTicket() {
        cas.enqueue(redirect(vpnUrl("/login?cas_login=true&ticket=ST-gateway")))
        vpn.enqueue(redirect("/home").addHeader("Set-Cookie", "SESSION=gateway; Path=/"))
        vpn.enqueue(success())
    }

    @Test fun `parses BIT text and input forms`() {
        assertEquals(CasInitDto("EXEC_TOKEN_42", "MDEyMzQ1Njc4OWFiY2RlZg=="), sso.parseLoginPage(fixture()))
        assertEquals(CasInitDto("flow", "salt"), sso.parseLoginPage(
            """<input value='flow' id='login-page-flowkey'><input id="login-croypto" value="salt">"""))
    }

    @Test fun `off campus grades establish gateway before consuming target ticket`() = runBlocking {
        passwordTicket()
        gatewayTicket()
        vpn.enqueue(success().addHeader("Set-Cookie", "SESSION=jwms; Path=/https/campus/"))
        // An absolute campus Location must stay inside the VPN, too.
        vpn.enqueue(redirect(portalUrl("/jsxsd/framework/xsMain.jsp")))
        vpn.enqueue(success())
        assertEquals(CasLoginDto.Success, sso.loginGrades(api, "student", "password"))
        assertEquals(0, portal.requestCount)
        val requests = (1..5).map { vpn.takeRequest() }
        assertEquals("ST-gateway", requests[0].requestUrl!!.queryParameter("ticket"))
        assertEquals("/https/campus/", requests[2].requestUrl!!.encodedPath)
        assertNull(requests[2].requestUrl!!.queryParameter("ticket"))
        assertEquals("ST-target", requests[3].requestUrl!!.queryParameter("ticket"))
        assertEquals("SESSION=jwms; SESSION=gateway", requests[3].getHeader("Cookie"))
        assertEquals("/https/campus/jsxsd/framework/xsMain.jsp", requests[4].path)
        val init = cas.takeRequest()
        val post = cas.takeRequest()
        val gateway = cas.takeRequest()
        assertEquals(portalUrl("/").toString(), init.requestUrl!!.queryParameter("service"))
        assertEquals(init.path, post.path)
        assertEquals("POST", post.method)
        assertEquals(casUrl("/").origin(), post.getHeader("Origin"))
        assertEquals(BitBrowserHeaders.USER_AGENT, post.getHeader("User-Agent"))
        assertTrue(post.body.readUtf8().contains("captcha_payload="))
        assertTrue(gateway.getHeader("Cookie").orEmpty().contains("TGC=trusted"))
    }

    @Test fun `campus login uses target directly without contacting VPN`() = runBlocking {
        api.open(NetworkMode.LOCAL)
        passwordTicket()
        portal.enqueue(success())
        portal.enqueue(redirect("/jsxsd/main"))
        portal.enqueue(success())
        assertEquals(CasLoginDto.Success, sso.loginGrades(api, "student", "pw"))
        assertEquals(0, vpn.requestCount)
        assertEquals(2, cas.requestCount)
        assertEquals(3, portal.requestCount)
    }

    @Test fun `dashboard logout handler must not be executed while following login`() = runBlocking {
        passwordTicket()
        gatewayTicket()
        vpn.enqueue(success())
        vpn.enqueue(MockResponse().setBody("""
            <html><body>教学服务主页
              <script>function logout() { window.location.href = '/jsxsd'; }</script>
              <button onclick="location.href='/cas/login'">退出</button>
            </body></html>
        """.trimIndent()))
        assertEquals(CasLoginDto.Success, sso.loginGrades(api, "student", "pw"))
        assertEquals(4, vpn.requestCount)
    }

    @Test fun `standalone script redirect still reaches service landing page`() = runBlocking {
        api.open(NetworkMode.LOCAL)
        passwordTicket()
        portal.enqueue(success())
        portal.enqueue(MockResponse().setBody("<script>window.location.href = '/jsxsd/main';</script>"))
        portal.enqueue(success())
        assertEquals(CasLoginDto.Success, sso.loginGrades(api, "student", "pw"))
        assertEquals(3, portal.requestCount)
        portal.takeRequest(); portal.takeRequest()
        assertEquals("/jsxsd/main", portal.takeRequest().path)
    }

    @Test fun `teaching portal nonce and ticket survive proxy rewriting`() = runBlocking {
        val service = portalUrl("/auth/callback?sessionToken=nonce%2B1&service=app%26query")
        portal.enqueue(redirect(casUrl("/cas/login").newBuilder().addQueryParameter("service", service.toString()).build()))
        passwordTicket(service)
        gatewayTicket()
        vpn.enqueue(redirect(urls.jwappLanding))
        vpn.enqueue(success())
        vpn.enqueue(success())
        assertEquals(CasLoginDto.Success, sso.invoke(api, "student", "pw"))
        assertEquals(1, portal.requestCount) // public auth preflight only
        assertNull(portal.takeRequest().getHeader("Cookie"))
        vpn.takeRequest(); vpn.takeRequest()
        val callback = vpn.takeRequest().requestUrl!!
        assertEquals("/https/campus/auth/callback", callback.encodedPath)
        assertEquals("nonce+1", callback.queryParameter("sessionToken"))
        assertEquals("app&query", callback.queryParameter("service"))
        assertEquals("ST-target", callback.queryParameter("ticket"))
    }

    @Test fun `existing verified TGC is reused without another password submission`() = runBlocking {
        jar.saveFromResponse(casUrl("/cas/login"), listOf(
            Cookie.Builder().name("TGC").value("verified").hostOnlyDomain("sso.test").path("/cas").build()))
        cas.enqueue(redirect(portalUrl("/?ticket=ST-target")))
        gatewayTicket()
        vpn.enqueue(success()); vpn.enqueue(success())
        assertEquals(CasLoginDto.Success, sso.loginGrades(api, "student", "pw"))
        assertEquals(2, cas.requestCount)
        repeat(2) { assertEquals("GET", cas.takeRequest().method) }
    }

    private fun failedPost(code: Int, body: String): CasLoginDto = runBlocking {
        cas.enqueue(MockResponse().setBody(fixture()))
        cas.enqueue(MockResponse().setResponseCode(code).setBody(body))
        sso.loginGrades(api, "student", "pw").also {
            assertEquals(0, vpn.requestCount)
            assertEquals(0, portal.requestCount)
        }
    }

    @Test fun `HTTP 401 wrong password is not success`() {
        assertEquals(CasLoginDto.WrongCredentials, failedPost(401, "用户名或密码错误"))
    }
    @Test fun `modern CAS hidden credential error takes precedence over login and SMS markup`() {
        assertEquals(CasLoginDto.WrongCredentials, failedPost(401,
            "<span id='login-error-msg'> </span><span id='login-error-code'>1030027</span>" +
                "<form id='normalLoginForm'></form><form id='smsLoginForm'>短信验证</form>"))
    }
    @Test fun `modern CAS hidden lock code does not ask for repeated password submission`() {
        assertEquals(CasLoginDto.AccountLocked, failedPost(401,
            "<span id='login-error-msg'>1030028</span><form id='normalLoginForm'></form>"))
    }
    @Test fun `generic authentication rejection is not misreported as an SMS challenge`() {
        val result = failedPost(401,
            "<span id='login-error-msg'>认证信息无效。</span><form id='normalLoginForm'></form>")
        assertTrue(result is CasLoginDto.UnknownFailure)
        assertTrue((result as CasLoginDto.UnknownFailure).body.contains("认证信息无效"))
    }
    @Test fun `locked account is reported before optional SMS form markup`() {
        assertEquals(CasLoginDto.AccountLocked, failedPost(200, "账号已锁定 <form id='secondSmsLoginForm'>"))
    }
    @Test fun `SMS form without old username password marker requires verification`() {
        assertEquals(CasLoginDto.CaptchaRequired, failedPost(200, "<form id='secondSmsLoginForm'>短信验证</form>"))
    }
    @Test fun `captcha page requires verification`() {
        assertEquals(CasLoginDto.CaptchaRequired, failedPost(200, "请输入验证码"))
    }
    @Test fun `HTTP 500 empty body is never success`() {
        assertTrue(failedPost(500, "") is CasLoginDto.UnknownFailure)
    }
    @Test fun `HTTP 200 without service ticket is never success`() {
        assertTrue(failedPost(200, "<html>Welcome 验证码管理</html>") is CasLoginDto.UnknownFailure)
    }
    @Test fun `ticket for an unexpected host is rejected without following it`() = runBlocking {
        cas.enqueue(MockResponse().setBody(fixture()))
        cas.enqueue(redirect("https://untrusted.invalid/?ticket=ST-secret"))
        val result = sso.loginGrades(api, "student", "pw")
        assertTrue(result is CasLoginDto.UnknownFailure)
        assertFalse((result as CasLoginDto.UnknownFailure).body.contains("ST-secret"))
    }
    @Test fun `gateway failure cannot be mistaken for target success`() = runBlocking {
        passwordTicket()
        cas.enqueue(redirect(vpnUrl("/login?cas_login=true&ticket=ST-gateway")))
        vpn.enqueue(MockResponse().setResponseCode(502))
        assertTrue(sso.loginGrades(api, "student", "pw") is CasLoginDto.UnknownFailure)
        assertEquals(1, vpn.requestCount)
        assertEquals(0, portal.requestCount)
    }
    @Test fun `target callback landing on expired session is not success`() = runBlocking {
        passwordTicket(); gatewayTicket()
        vpn.enqueue(success())
        vpn.enqueue(MockResponse().setBody("<script>alert('该帐号不存在或密码错误')</script>"))
        assertEquals(CasLoginDto.CaptchaRequired, sso.loginGrades(api, "student", "pw"))
    }
    @Test fun `public lexue service does not depend on teaching portal or VPN`() = runBlocking {
        passwordTicket(portalUrl("/login/index.php"))
        portal.enqueue(success())
        assertEquals(CasLoginDto.Success, sso.loginLexue(api, "student", "pw"))
        assertEquals(0, vpn.requestCount)
        assertEquals(1, portal.requestCount)
        assertEquals("/login/index.php", portal.takeRequest().requestUrl!!.encodedPath)
    }

    @Test fun `新乐学 login follows its identity broker to CAS and back, never through WebVPN`() = runBlocking {
        // setUp opened the session in WebVPN mode; 新乐学 is public and must still go direct.
        val brokerEndpoint = idpUrl("/auth/realms/bit/broker/cas-client/endpoint?state=s1")
        eclass.enqueue(redirect(idpUrl("/auth/realms/bit/protocol/cas/login?service=" + eclassUrl("/user/courses"))))
        idp.enqueue(redirect(idpUrl("/auth/realms/bit/broker/cas-client/login?session_code=c1"))
            .addHeader("Set-Cookie", "AUTH_SESSION_ID=kc; Path=/auth/realms/bit/"))
        // zy-identity writes the CAS address with a double slash.
        idp.enqueue(redirect(casUrl("/cas//login").newBuilder().addQueryParameter("service", brokerEndpoint.toString()).build()))
        passwordTicket(brokerEndpoint)
        idp.enqueue(redirect(eclassUrl("/user/courses?ticket=ST-kc")))
        eclass.enqueue(redirect("/user/courses").addHeader("Set-Cookie", "session=signed; Path=/"))
        eclass.enqueue(success())

        assertEquals(CasLoginDto.Success, sso.loginEclass(api, "student", "password"))

        assertEquals(0, vpn.requestCount)
        val init = cas.takeRequest()
        assertEquals(brokerEndpoint.toString(), init.requestUrl!!.queryParameter("service"))
        assertEquals("POST", cas.takeRequest().method)
        val idpRequests = (1..3).map { idp.takeRequest() }
        assertEquals("ST-target", idpRequests[2].requestUrl!!.queryParameter("ticket"))
        assertEquals("s1", idpRequests[2].requestUrl!!.queryParameter("state"))
        assertEquals("AUTH_SESSION_ID=kc", idpRequests[2].getHeader("Cookie"))
        val eclassRequests = (1..3).map { eclass.takeRequest() }
        assertEquals("ST-kc", eclassRequests[1].requestUrl!!.queryParameter("ticket"))
        assertEquals("session=signed", eclassRequests[2].getHeader("Cookie"))
    }

    @Test fun `新乐学 login is a no-op while its session is still valid`() = runBlocking {
        eclass.enqueue(success())
        assertEquals(CasLoginDto.Success, sso.loginEclass(api, "student", "password"))
        assertEquals(0, cas.requestCount)
        assertEquals(0, idp.requestCount)
    }

    @Test fun `新乐学 login reports a wrong password from CAS`() = runBlocking {
        val brokerEndpoint = idpUrl("/auth/realms/bit/broker/cas-client/endpoint?state=s2")
        eclass.enqueue(redirect(idpUrl("/auth/realms/bit/broker/cas-client/login?session_code=c2")))
        idp.enqueue(redirect(casUrl("/cas//login").newBuilder().addQueryParameter("service", brokerEndpoint.toString()).build()))
        cas.enqueue(MockResponse().setBody(fixture()))
        cas.enqueue(MockResponse().setBody("""<div id="login-error-code">1030027</div>"""))
        assertEquals(CasLoginDto.WrongCredentials, sso.loginEclass(api, "student", "wrong"))
    }

    @Test fun `CAS address with repeated slashes is still CAS`() {
        assertTrue(urls.isCas(casUrl("/cas//login")))
        assertTrue(urls.isCas(casUrl("/cas/login/")))
        assertFalse(urls.isCas(casUrl("/cas/logout")))
        assertTrue(urls.trusted(eclassUrl("/api/my-semesters")))
        assertTrue(urls.trusted(idpUrl("/auth/realms/bit/broker/cas-client/endpoint")))
    }

    // ---- SMS second step and picture captcha, answered in the app ----

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    /** The page CAS shows after a right password when it also wants an SMS code. */
    private fun secondStepPage(service: HttpUrl) = MockResponse().setBody(
        """<html><div id="login-page-flowkey">flow2</div><div id="user-object-id">obj-9</div>
           <div id="second-auth-tip">短信验证</div>
           <form id="secondSmsLoginForm" method="post" action="login?service=${service}"></form></html>""",
    )

    /** Answers like a person: types [codes] in turn, or leaves for the school's page. */
    private class FakePrompter(
        private val codes: List<String> = emptyList(),
        private val trust: Boolean = true,
        private val useWebPage: Boolean = false,
    ) : CasPrompter {
        var phoneTail: String? = null
        val rejections = mutableListOf<String>()
        var captchaImage: ByteArray? = null

        override suspend fun sms(challenge: SmsChallenge): SmsDecision {
            phoneTail = challenge.phoneTail
            if (useWebPage) return SmsDecision.UseWebPage
            assertEquals(SmsSendResult.Sent(60), challenge.send())
            for (code in codes) {
                when (val r = challenge.check(code, trust)) {
                    SmsCheckResult.Accepted -> return SmsDecision.Verified(code, trust)
                    is SmsCheckResult.Rejected -> rejections += r.message
                }
            }
            return SmsDecision.UseWebPage
        }

        override suspend fun captcha(image: ByteArray): CaptchaDecision {
            captchaImage = image
            return if (useWebPage) CaptchaDecision.UseWebPage else CaptchaDecision.Answer("ab12")
        }
    }

    private fun teachingPortal(): HttpUrl {
        api.open(NetworkMode.LOCAL)
        val service = portalUrl("/auth/callback?sessionToken=n1")
        portal.enqueue(redirect(casUrl("/cas/login").newBuilder().addQueryParameter("service", service.toString()).build()))
        cas.enqueue(MockResponse().setBody(fixture()))
        return service
    }

    private fun formOf(request: okhttp3.mockwebserver.RecordedRequest): Map<String, String> =
        request.body.readUtf8().split('&').associate {
            val (k, v) = it.split('=', limit = 2)
            java.net.URLDecoder.decode(k, "UTF-8") to java.net.URLDecoder.decode(v, "UTF-8")
        }

    @Test fun `SMS second step is answered in the app and then issues the ticket`() = runBlocking {
        val service = teachingPortal()
        cas.enqueue(json("""{"code":200,"data":{"captchaInvisible":false}}"""))
        cas.enqueue(secondStepPage(service))
        cas.enqueue(json("""{"code":200,"data":{"URL_CRYPTO_DATA":{"SID":["/api/protected/sms/getPhoneNumberByUserId"]}}}"""))
        cas.enqueue(json("""{"code":200,"data":{"tel":"opaque-7","maskTel":"138****1234"}}"""))
        cas.enqueue(json("""{"code":200,"message":"OK"}"""))
        cas.enqueue(json("""{"code":400,"message":"验证码错误"}"""))
        cas.enqueue(json("""{"code":200}"""))
        cas.enqueue(redirect(service.newBuilder().addQueryParameter("ticket", "ST-target").build()))
        portal.enqueue(success())
        portal.enqueue(success())
        val prompter = FakePrompter(codes = listOf("000000", "123456"))

        assertEquals(CasLoginDto.Success, sso.invoke(api, "student", "password", prompter))

        assertEquals("1234", prompter.phoneTail)
        assertEquals(listOf("验证码错误"), prompter.rejections)
        val requests = (1..9).map { cas.takeRequest(2, TimeUnit.SECONDS)!! }
        assertEquals("/cas/login", requests[0].requestUrl!!.encodedPath)
        assertEquals("/cas/api/protected/user/findCaptchaCount/student", requests[1].requestUrl!!.encodedPath)
        assertTrue(requests[1].getHeader("Csrf-Key").orEmpty().length == 32)
        assertEquals("UsernamePassword", formOf(requests[2])["type"])
        // The config call and the phone lookup are encrypted like the school's page does it.
        assertEquals("/gate/sso-extend/protected/api/dictconfig/get", requests[3].requestUrl!!.encodedPath)
        assertEquals("/cas/api/protected/sms/getPhoneNumberByUserId", requests[4].requestUrl!!.encodedPath)
        assertEquals("true", requests[4].getHeader("hasCrypto"))
        assertTrue(requests[4].getHeader("privateKey").orEmpty().isNotEmpty())
        assertFalse(requests[4].body.readUtf8().contains("obj-9"))
        assertEquals("""{"phone":"opaque-7","businessNo":"0008"}""", requests[5].body.readUtf8())
        assertNull(requests[5].getHeader("hasCrypto"))
        assertTrue(requests[6].body.readUtf8().contains("\"token\":\"000000\""))
        assertEquals("""{"phone":"opaque-7","token":"123456","delete":false,"trustDevice":true}""", requests[7].body.readUtf8())
        val final = formOf(requests[8])
        assertEquals("smsLogin", final["type"])
        assertEquals("123456", final["password"])
        assertEquals("flow2", final["execution"])
        assertEquals("true", final["trustDevice"])
        assertEquals(service.toString(), requests[8].requestUrl!!.queryParameter("service"))
        assertEquals(0, vpn.requestCount)
        assertEquals(3, portal.requestCount)
    }

    @Test fun `background sign-in stops at the SMS step without sending anything`() = runBlocking {
        cas.enqueue(MockResponse().setBody(fixture()))
        cas.enqueue(secondStepPage(portalUrl("/")))
        assertEquals(CasLoginDto.CaptchaRequired, sso.loginGrades(api, "student", "password"))
        assertEquals(2, cas.requestCount)
    }

    @Test fun `choosing the school's page leaves the SMS step unfinished`() = runBlocking {
        val service = teachingPortal()
        cas.enqueue(json("""{"code":200,"data":{"captchaInvisible":false}}"""))
        cas.enqueue(secondStepPage(service))
        cas.enqueue(json("""{"code":200,"data":{"URL_CRYPTO_DATA":{"SID":[]}}}"""))
        cas.enqueue(json("""{"code":200,"data":{"tel":"opaque-7","maskTel":"138****1234"}}"""))
        assertEquals(CasLoginDto.CaptchaRequired, sso.invoke(api, "student", "password", FakePrompter(useWebPage = true)))
        assertEquals(5, cas.requestCount) // no code sent, no final form
    }

    @Test fun `no bound phone sends the person to the school's page`() = runBlocking {
        val service = teachingPortal()
        cas.enqueue(json("""{"code":200,"data":{"captchaInvisible":false}}"""))
        cas.enqueue(secondStepPage(service))
        cas.enqueue(json("""{"code":500,"message":"busy"}"""))
        cas.enqueue(json("""{"code":500,"message":"busy"}"""))
        val prompter = FakePrompter(codes = listOf("123456"))
        assertEquals(CasLoginDto.CaptchaRequired, sso.invoke(api, "student", "password", prompter))
        assertNull(prompter.phoneTail) // never asked
    }

    @Test fun `picture captcha is typed in the app and sent with the password`() = runBlocking {
        val service = teachingPortal()
        cas.enqueue(json("""{"code":200,"data":{"captchaInvisible":true,"captchaUrl":"data:image/png;base64,AQID","captchaPayload":{"id":"c1"}}}"""))
        cas.enqueue(redirect(service.newBuilder().addQueryParameter("ticket", "ST-target").build()))
        portal.enqueue(success())
        portal.enqueue(success())
        val prompter = FakePrompter()

        assertEquals(CasLoginDto.Success, sso.invoke(api, "student", "password", prompter))

        assertArrayEquals(byteArrayOf(1, 2, 3), prompter.captchaImage)
        repeat(2) { cas.takeRequest(2, TimeUnit.SECONDS)!! }
        val form = formOf(cas.takeRequest(2, TimeUnit.SECONDS)!!)
        assertEquals("ab12", form["captcha_code"])
        assertEquals(CasAesCrypto.encryptPassword("""{"id":"c1"}""", "MDEyMzQ1Njc4OWFiY2RlZg=="), form["captcha_payload"])
    }

    @Test fun `a captcha check the CAS cannot answer does not block the password`() = runBlocking {
        val service = teachingPortal()
        cas.enqueue(MockResponse().setResponseCode(404).setBody("<html>not found</html>"))
        cas.enqueue(redirect(service.newBuilder().addQueryParameter("ticket", "ST-target").build()))
        portal.enqueue(success())
        portal.enqueue(success())
        val prompter = FakePrompter()
        assertEquals(CasLoginDto.Success, sso.invoke(api, "student", "password", prompter))
        assertNull(prompter.captchaImage)
        repeat(2) { cas.takeRequest(2, TimeUnit.SECONDS)!! }
        assertEquals("", formOf(cas.takeRequest(2, TimeUnit.SECONDS)!!)["captcha_code"])
    }
}
