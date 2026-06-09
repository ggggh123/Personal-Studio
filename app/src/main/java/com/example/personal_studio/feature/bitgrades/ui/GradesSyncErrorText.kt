package com.example.personal_studio.feature.bitgrades.ui

import com.example.personal_studio.domain.bitgrades.model.GradesSyncError

/**
 * 把成绩同步失败映射成具体的中文提示——比泛泛的「同步失败」更利于排障。
 *
 * 网络/未知错误会带上底层异常的类型与信息（如 `UnknownHostException: ... jwms.bit.edu.cn`），
 * 这正是定位校外/会话问题的关键线索，所以不吞掉。纯函数，方便单测。
 */
fun GradesSyncError.userMessage(): String = when (this) {
    GradesSyncError.WrongCredentials -> "用户名或密码错误，请重新登录"
    GradesSyncError.AccountLocked    -> "账号已被锁定，请稍后再试或到教务系统解锁"
    GradesSyncError.CaptchaRequired  -> "登录需要验证码，请先在教务系统网页端登录一次再试"
    GradesSyncError.NeedReview       -> "需先在教务系统完成评教才能查成绩"
    GradesSyncError.EmptyGrades      -> "没拉到成绩（可能是会话失效、页面结构变化，或该学期暂无成绩）"
    is GradesSyncError.ParseFail     -> "解析失败：$message"
    is GradesSyncError.NetworkFail   -> "网络不可达：${cause.describe()}"
    is GradesSyncError.Unexpected    -> "出错了：${cause.describe()}"
}

/** 异常 → 「类型: 信息」紧凑串；信息为空时只给类型。 */
private fun Throwable.describe(): String {
    val type = this::class.java.simpleName.ifBlank { "Error" }
    val msg = message?.trim().orEmpty()
    return if (msg.isEmpty()) type else "$type: $msg"
}
