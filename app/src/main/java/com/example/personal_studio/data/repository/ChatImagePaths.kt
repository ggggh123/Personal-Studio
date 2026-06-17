package com.example.personal_studio.data.repository

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val chatImagePathsJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** 聊天消息的图路径列表 ⇄ `chat_messages.attachedImagePath` 列(复用单列存 JSON 数组)。 */

/** 序列化:空列表→null(列存 null),否则存 JSON 数组字符串。 */
internal fun encodeChatImagePaths(paths: List<String>): String? =
    if (paths.isEmpty()) null else chatImagePathsJson.encodeToString(paths)

/** 反序列化:null/空→[];JSON 数组→列表;解析失败(旧的裸单路径)→单元素列表。 */
internal fun decodeChatImagePaths(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { chatImagePathsJson.decodeFromString<List<String>>(raw) }
        .getOrElse { listOf(raw) }
}
