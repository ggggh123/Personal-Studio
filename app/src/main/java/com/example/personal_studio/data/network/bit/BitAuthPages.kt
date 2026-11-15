package com.example.personal_studio.data.network.bit

import com.example.personal_studio.data.network.bit.dto.CasLoginDto

/** Shared response checks. Never put response bodies or query strings into diagnostics. */
object BitAuthPages {
    fun isLoginPage(html: String): Boolean = listOf(
        "login-page-flowkey", "normalLoginForm", "smsLoginForm", "secondSmsLoginForm",
        "cas-gateway", "帐号登录或动态码登录", "用户名密码",
    ).any { html.contains(it, ignoreCase = true) }

    fun needsVerification(html: String): Boolean = listOf(
        "secondSmsLoginForm", "短信验证", "短信动态码", "请输入验证码", "验证码不能为空",
    ).any { html.contains(it, ignoreCase = true) }

    fun loginFailure(html: String): CasLoginDto? {
        // The current CAS renders these hidden fields in JavaScript. The raw
        // response may contain no visible error text, even when login was rejected.
        val error = listOf("login-error-msg", "login-error-code").mapNotNull { id ->
            Regex("""<[^>]+\bid\s*=\s*["']$id["'][^>]*>\s*([^<]*)""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        }
        when {
            error.any { it == "1030027" || it == "1030031" } -> return CasLoginDto.WrongCredentials
            "1030028" in error -> return CasLoginDto.AccountLocked
            error.any { "认证信息无效" in it } -> return CasLoginDto.UnknownFailure(
                "学校返回“认证信息无效”，请在学校网页确认账号密码或改用短信登录。",
            )
        }
        return when {
            listOf("用户名或密码错误", "用户名或密码不正确", "账号或密码错误").any { it in html } -> CasLoginDto.WrongCredentials
            listOf("账号已锁定", "账号已被锁定", "帐号已锁定").any { it in html } -> CasLoginDto.AccountLocked
            needsVerification(html) -> CasLoginDto.CaptchaRequired
            isLoginPage(html) -> CasLoginDto.CaptchaRequired
            else -> null
        }
    }

    fun hasGradeTable(html: String): Boolean = Regex(
        """<table\b[^>]*\bid\s*=\s*["']dataList["']""", RegexOption.IGNORE_CASE,
    ).containsMatchIn(html)

    fun isSessionFailure(html: String): Boolean = isLoginPage(html) || listOf(
        "该帐号不存在或密码错误", "会话已过期", "会话失效", "请重新登录",
    ).any { it in html } || scriptRedirect(html)?.let {
        "/cas/login" in it || "/authserver/login" in it
    } == true

    fun scriptRedirect(html: String): String? {
        // A dashboard can contain location assignments inside logout handlers,
        // conditions and click attributes. Those are not automatic redirects.
        // Only support a script whose entire body is a literal redirect statement.
        val statement = Regex("""\s*(?:window\.)?location(?:\.href)?\s*=\s*["']([^"']+)["']\s*;?\s*""")
        return Regex("""<script\b[^>]*>([\s\S]*?)</script\s*>""", RegexOption.IGNORE_CASE)
            .findAll(html).mapNotNull { statement.matchEntire(it.groupValues[1])?.groupValues?.get(1) }
            .firstOrNull()?.replace("&amp;", "&")
    }
}
