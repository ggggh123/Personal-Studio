package com.example.personal_studio.ui.navigation

object NavRoutes {
    // Bottom-nav tabs
    const val CHAT = "chat"
    const val SCANNER = "scanner"
    const val KNOWLEDGE = "knowledge"
    const val TIMELINE = "timeline"

    // Sub-destinations
    const val CHAT_DETAIL = "chat/detail/{sessionId}"
    fun chatDetail(sessionId: Long) = "chat/detail/$sessionId"

    const val SETTINGS = "settings"
}
