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

数据中含有的关键字段说明:
- 每门课的「成绩」「绩点」是学生本人;「班均」是该课所有学生平均分;「班级前X%」「专业前Y%」是学生在该课的相对位次(数字越小越靠前)。
- 行首「(参考)估计班级平均水平」是按各课班均加权后的整体基准。可拿来判断学生整体是高于/接近/低于"普通同学"水平。
- 部分课程名以「思政课N」匿名出现(规避内容过滤),分析时按一门普通课对待即可。

写作要求:
- 中文,具体,可执行。判断"强/弱"要用相对位次或与班均的差(不只是绝对分数)。
- 必须**点名具体课程/学期**作证据;引用数字时保留 1-2 位小数。
- 用 markdown:小标题 ## 、要点列表 -、必要时**加粗**。
- 不要寒暄、不要客套结尾、不编造数据里没有的信息。"""
    }
}
