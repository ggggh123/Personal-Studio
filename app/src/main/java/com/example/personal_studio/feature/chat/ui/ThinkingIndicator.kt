package com.example.personal_studio.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlin.random.Random

/** 模型思考(尤其推理模型出首字前)空窗期轮播的幽默提示词。终端黑话 + 教务/学生梗,共 24 条。 */
val THINKING_PHRASES: List<String> = listOf(
    "正在翻越教务系统防火墙…",
    "召唤算力中，请勿拔网线…",
    "正在给神经元灌咖啡…",
    "检索平行宇宙里的标准答案…",
    "正在和 GPU 讨价还价…",
    "编译思路中，暂未发现语法错误…",
    "正在偷看学霸的草稿纸…",
    "加载脑细胞，内存已用 99%…",
    "正在向教务处申请延长思考时间…",
    "推理链展开中，请勿眨眼…",
    "把咖啡因转换成 token 中…",
    "翻遍三层缓存还在想…",
    "正在绕开早八的困意…",
    "给思路做去重和排序…",
    "正在质疑这道题的出题人…",
    "量子叠加态收敛中…",
    "正在 fork 一条新思路…",
    "梯度下降到答案谷底中…",
    "正在向助教借一根脑回路…",
    "正在 ctrl+F 整个知识库…",
    "把脑子超频到 5GHz…",
    "正在补昨晚没看的网课…",
    "给这道题办场开卷考试…",
    "正在 sudo 提升思考权限…",
)

/** 第 [elapsedSeconds] 秒、随机起始 [offset] 时该显示的提示句。每 3 秒进一句,取模循环。 */
fun thinkingPhraseAt(elapsedSeconds: Int, offset: Int): String =
    THINKING_PHRASES[(offset + elapsedSeconds / 3) % THINKING_PHRASES.size]

/**
 * 思考态指示:轮播提示句 + 已等待秒数 + 呼吸圆点。放在流式回答位置、`streamingText` 还为空时显示;
 * 一旦出真实 token 即由调用方切回正文。每次发送都是新实例,故 elapsed 从 0 起。
 */
@Composable
fun ThinkingIndicator(modifier: Modifier = Modifier) {
    val offset = remember { Random.nextInt(THINKING_PHRASES.size) }
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsed += 1
        }
    }
    ThinkingLine(phrase = thinkingPhraseAt(elapsed, offset), seconds = elapsed, modifier = modifier)
}
