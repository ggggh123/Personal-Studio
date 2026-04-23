package com.example.personal_studio.ui.navigation

import android.net.Uri

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

    // Scanner single-page smoke flow (expanded into DocumentBuilder host in Phase 3).
    const val SCANNER_CAMERA = "scanner/camera"

    const val SCANNER_EDGE = "scanner/edge?tmp={tmp}"
    fun scannerEdge(tmp: String) = "scanner/edge?tmp=${Uri.encode(tmp)}"

    const val SCANNER_ENHANCE = "scanner/enhance?tmp={tmp}&corners={corners}"
    fun scannerEnhance(tmp: String, corners: String) =
        "scanner/enhance?tmp=${Uri.encode(tmp)}&corners=${Uri.encode(corners)}"
}
