package com.example.personal_studio.domain.model

/** Source category of a KB entry — drives prompt assembly + back-link. */
enum class KbSource { CHAT_MESSAGE, CHAT_SESSION, SCAN }

/** Pure domain category (no Room types). */
data class KbCategory(
    val id: Long,
    val name: String,
    val seeded: Boolean,
)

/** Pure domain entry. `categoryId == null` is allowed; UI groups it under "其它". */
data class KbEntry(
    val id: Long,
    val title: String,
    val categoryId: Long?,
    val categoryName: String?,                  // joined from kb_categories (null when categoryId is null)
    val source: KbSource,
    val sourceChatMessageId: Long?,
    val sourceChatSessionId: Long?,
    val sourceScanPageId: Long?,
    val originalImagePath: String?,
    val standardizedQuestion: String?,          // non-null = belongs to mistakes collection
    val summaryMarkdown: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isMistake: Boolean get() = standardizedQuestion != null
}

/** Where the draft was triggered from. Sealed so Repository pattern-matches it. */
sealed class KbDraftSource {
    data class FromChatMessage(val sessionId: Long, val aiMessageId: Long) : KbDraftSource()
    data class FromChatSession(val sessionId: Long) : KbDraftSource()
    data class FromScanPage(val docId: Long, val pageId: Long) : KbDraftSource()
}

/** Why a draft is in fallback state (used by SavePreviewModal banner). */
enum class KbDraftFallbackReason { JSON_PARSE_FAILED, MISSING_REQUIRED_FIELDS }

/**
 * In-flight draft returned by [draftFromSource]. Either a parsed AI response or a
 * fallback skeleton (when LLM JSON couldn't be parsed twice). User edits in the
 * SavePreviewModal then commits — see KnowledgeRepository.saveEntry.
 */
data class KbEntryDraft(
    val source: KbDraftSource,
    val title: String,
    val categorySuggestion: String,             // resolved against existing categories at commit time
    val standardizedQuestion: String?,          // null when isQuestion=false
    val summaryMarkdown: String,
    val relatedEntryTitles: List<String>,
    val originalImagePath: String?,             // staged copy in filesDir/kb/tmp_<uuid>.jpg
    val isFallback: Boolean = false,
    val fallbackReason: KbDraftFallbackReason? = null,
)
