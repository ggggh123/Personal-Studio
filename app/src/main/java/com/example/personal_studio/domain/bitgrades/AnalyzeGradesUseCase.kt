package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.remote.llm.LLMProvider
import com.example.personal_studio.data.remote.llm.LlmChunk
import com.example.personal_studio.data.remote.llm.LlmMessage
import com.example.personal_studio.data.remote.llm.LlmRole
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/** 流式 AI 学情报告。emit 文本增量；出错抛异常由 ViewModel 捕获。 */
class AnalyzeGradesUseCase @Inject constructor(
    private val llm: LLMProvider,
    private val buildSummary: BuildGradeSummaryUseCase,
) {
    fun invoke(book: GradeBook): Flow<String> = flow {
        val summary = buildSummary.invoke(book)
        llm.generate(
            messages = listOf(
                LlmMessage(LlmRole.SYSTEM, SYSTEM_PROMPT),
                LlmMessage(LlmRole.USER, summary),
            ),
        ).collect { chunk ->
            when (chunk) {
                is LlmChunk.Text -> emit(chunk.delta)
                is LlmChunk.Error -> throw RuntimeException(chunk.message)
                is LlmChunk.Done -> {}
            }
        }
    }

    companion object {
        const val SYSTEM_PROMPT = """你是嵌在终端风学习 App 里的学业分析助手。基于给定成绩单数据输出一份简洁报告，分四部分，用 markdown 小标题：
## 趋势
## 强项
## 弱项
## 建议
要求：中文、具体、可执行；点名具体课程/学期作证据；不要寒暄、不要客套结尾；不编造数据里没有的信息。"""
    }
}
