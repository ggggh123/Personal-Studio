package com.example.personal_studio.feature.bitgrades.ui

import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class GradesSyncErrorTextTest {

    @Test fun `each variant maps to a non-generic message`() {
        assertEquals("用户名或密码错误，请重新登录", GradesSyncError.WrongCredentials.userMessage())
        assertEquals("账号已被锁定，请稍后再试或到教务系统解锁", GradesSyncError.AccountLocked.userMessage())
        assertEquals("登录需要验证码，请先在教务系统网页端登录一次再试", GradesSyncError.CaptchaRequired.userMessage())
        assertEquals("需先在教务系统完成评教才能查成绩", GradesSyncError.NeedReview.userMessage())
        assertTrue(GradesSyncError.EmptyGrades.userMessage().contains("没拉到成绩"))
        assertEquals("解析失败：CAS: boom", GradesSyncError.ParseFail("CAS: boom").userMessage())
    }

    @Test fun `network failure surfaces the underlying host for off-campus diagnosis`() {
        val err = GradesSyncError.NetworkFail(
            UnknownHostException("Unable to resolve host \"jwms.bit.edu.cn\""),
        )
        val msg = err.userMessage()
        // The whole point: the failing host is shown, not swallowed.
        assertTrue(msg, msg.contains("jwms.bit.edu.cn"))
        assertTrue(msg, msg.contains("UnknownHostException"))
    }

    @Test fun `unexpected failure falls back to exception type when message is null`() {
        val msg = GradesSyncError.Unexpected(RuntimeException()).userMessage()
        assertTrue(msg, msg.contains("出错了"))
        assertTrue(msg, msg.contains("RuntimeException"))
    }
}
