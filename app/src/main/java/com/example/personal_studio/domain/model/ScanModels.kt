package com.example.personal_studio.domain.model

enum class ScanFilter { COLOR, GRAYSCALE, BW }

enum class SortMode { TIME_DESC, ALPHA_ASC, RECENT_UPDATED }

data class ScanDocument(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val coverPageId: Long?,
)

data class ScanPage(
    val id: Long,
    val docId: Long,
    val ordinal: Int,
    val originalImagePath: String,
    val enhancedImagePath: String,
    val filter: ScanFilter,
    val cornersJson: String?,
    val createdAt: Long,
)

/** 库列表富行:文档 + 封面(首页 enhanced 图)路径。 */
data class ScanDocumentSummary(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val coverPageId: Long?,
    val coverPath: String?,
)
