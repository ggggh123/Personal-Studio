package com.example.personal_studio.core.llm

/**
 * A built-in model: shown name, the id sent to the API, a one-line description, and whether it
 * reads images. All of them use the built-in DeepSeek endpoint and key.
 */
data class CuratedModel(val display: String, val code: String, val tagline: String, val readsImages: Boolean)

/** The built-in models (DeepSeek's two, from its `/models` list, 2026-11). */
object CuratedModels {
    val FLASH = CuratedModel(
        "DeepSeek Flash", "deepseek-flash",
        "V4.1 · 回答快，能看图片，适合日常问答和拍题", readsImages = true,
    )
    val PRO = CuratedModel(
        "DeepSeek Pro", "deepseek-v4-pro",
        "V4 · 推理更强、稍慢；只读文字，带图片的提问交给 Flash", readsImages = false,
    )
    val ALL: List<CuratedModel> = listOf(FLASH, PRO)
    val DEFAULT: CuratedModel = FLASH

    /** DeepSeek still accepts the retired Flash names and serves them with the current Flash. */
    private val ALIASES = mapOf("deepseek-v4-flash" to FLASH, "deepseek-v4-flash-vision-exp" to FLASH)

    fun of(code: String?): CuratedModel? = ALL.firstOrNull { it.code == code } ?: ALIASES[code]

    /** Code → shown name; null for a custom model or null. */
    fun displayFor(code: String?): String? = of(code)?.display
}
