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

    const val SCANNER_EDGE = "scanner/edge?tmp={tmp}&auto={auto}&live={live}"
    /**
     * [live] is an optional pre-detected quadrilateral in normalized
     * portrait coordinates (values 0..1, encoded "x,y;x,y;x,y;x,y"). Empty
     * string when no live detection was available at capture time.
     */
    fun scannerEdge(tmp: String, auto: Boolean, live: String) =
        "scanner/edge?tmp=${Uri.encode(tmp)}&auto=$auto&live=${Uri.encode(live)}"

    const val SCANNER_ENHANCE = "scanner/enhance?tmp={tmp}&corners={corners}"
    fun scannerEnhance(tmp: String, corners: String) =
        "scanner/enhance?tmp=${Uri.encode(tmp)}&corners=${Uri.encode(corners)}"
}
