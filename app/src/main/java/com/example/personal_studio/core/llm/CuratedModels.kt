package com.example.personal_studio.core.llm

/** 一个内置可选模型:展示名 + 调用 API 时的实际模型代号。 */
data class CuratedModel(val display: String, val code: String)

/** 内置精选模型名单(共用 App 内置端点+密钥;来源见仓库外 AI-models 文件)。 */
object CuratedModels {
    val ALL: List<CuratedModel> = listOf(
        CuratedModel("Claude Opus 4.8", "claude-opus-4-8"),
        CuratedModel("Claude Sonnet 4.6", "claude-sonnet-4-6"),
        CuratedModel("GPT 5.5", "gpt-5.5"),
        CuratedModel("Gemini 3.5 Flash", "gemini-3.5-flash"),
        CuratedModel("Gemini 3.1 Pro", "gemini-3.1-pro-preview"),
        CuratedModel("Deepseek V4 Flash", "deepseek-v4-flash"),
        CuratedModel("Deepseek V4 Pro", "deepseek-v4-pro"),
        CuratedModel("GLM 5.2", "glm-5.2"),
        CuratedModel("Kimi 2.6", "kimi-2.6"),
        CuratedModel("Minimax M3", "MiniMax-M3"),
        CuratedModel("豆包", "doubao-seed-2-0-lite-260428"),
    )

    /** 代号→展示名;非名单内(自定义)或 null 返回 null。 */
    fun displayFor(code: String?): String? = ALL.firstOrNull { it.code == code }?.display
}
