package com.example.personal_studio.data.local.datastore

/**
 * 后台轮询「上次结果」,持久化供设置页展示。每次轮询的**任一终态**(成功/停轮/瞬时失败/异常)都记一次,
 * 这样失败也可见(此前只在成功时写 lastSyncAt → 失败无痕)。
 */
data class PollResult(
    val at: Long,          // 上次尝试时刻
    val ok: Boolean,       // 成功 or 失败
    val total: Int,        // 拉到多少条(成功时;失败为 0)
    val newCount: Int,     // 本次新增几条
    val message: String,   // 失败原因 / 特殊说明(如"已建立基线");普通成功为空串
)
