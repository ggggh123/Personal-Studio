package com.example.personal_studio.data.network.bit

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** 校内↔校外的另一种模式。 */
fun otherMode(m: NetworkMode): NetworkMode =
    if (m == NetworkMode.LOCAL) NetworkMode.WEBVPN else NetworkMode.LOCAL

/**
 * 校内↔校外自动回退(Flow 形态)。先按 [first] 跑 [attempt];若以「连接级失败」([isConnFail] 判真)
 * 收尾且还有另一 mode,**抑制**那个失败、emit [switchingStep] 提示,再换 mode 重试一次。
 * [isDone] 判真即停并回告 [onModeSucceeded];其余终态(非连接级失败 / Cancelled)也停、原样 surface。
 *
 * **有意取舍**:只在连接级失败(各功能的 NetworkFail / IOException)回退——其余失败可能是真实状态
 * (无数据 / 评教未完成 / 密码错),盲目翻网会掩盖真因。纯函数,不依赖 BitApiClient,便于单测。
 * 设计见 docs/superpowers/specs/2026-06-09-network-auto-fallback-design.md。
 */
fun <S> autoNetworkFallback(
    first: NetworkMode,
    isConnFail: (S) -> Boolean,
    isDone: (S) -> Boolean,
    switchingStep: (NetworkMode) -> S,
    onModeSucceeded: (NetworkMode) -> Unit,
    attempt: (NetworkMode) -> Flow<S>,
): Flow<S> = flow {
    val modes = listOf(first, otherMode(first))
    for ((idx, mode) in modes.withIndex()) {
        val hasFallbackLeft = idx < modes.lastIndex
        var terminal: S? = null
        attempt(mode).collect { step ->
            terminal = step
            val suppress = isConnFail(step) && hasFallbackLeft   // 憋住首选的连接级失败(后面换模式)
            if (!suppress) emit(step)
        }
        val t = terminal ?: return@flow                          // 空流,无终态,停
        when {
            isDone(t) -> { onModeSucceeded(mode); return@flow }
            isConnFail(t) && hasFallbackLeft -> emit(switchingStep(otherMode(mode)))
            else -> return@flow
        }
    }
}

/**
 * 校内↔校外自动回退(suspend 形态)。先按 [first] 跑 [block];抛出 [isConnFail] 判真的异常
 * (默认 [java.io.IOException])且还有另一 mode → 换 mode 重试一次([onSwitching] 提示)。
 * 正常返回即回告 [onModeSucceeded] 并返回结果;非连接级异常直接抛出(不回退)。
 *
 * 用于会话拆成多次 open/close 的功能(如空教室):把「open→login→取数」整段作 [block],
 * 由调用方在 sessionMutex.withLock 内调用,使回退重试也在同一锁内完成。
 */
suspend fun <T> withSessionAutoFallback(
    first: NetworkMode,
    isConnFail: (Throwable) -> Boolean = { it is java.io.IOException },
    onModeSucceeded: (NetworkMode) -> Unit,
    onSwitching: (NetworkMode) -> Unit = {},
    block: suspend (NetworkMode) -> T,
): T {
    val modes = listOf(first, otherMode(first))
    for ((idx, mode) in modes.withIndex()) {
        try {
            val r = block(mode)
            onModeSucceeded(mode)
            return r
        } catch (e: Throwable) {
            if (!(idx < modes.lastIndex && isConnFail(e))) throw e
            onSwitching(otherMode(mode))
        }
    }
    error("withSessionAutoFallback: unreachable")
}
