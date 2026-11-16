package com.example.personal_studio.ui.navigation

import android.net.Uri

object NavRoutes {
    // Bottom-nav tabs (天光): 今日 · 问答 · ＋ · 资料 · 校园
    const val TIMELINE = "timeline"
    const val CHAT = "chat"
    /** 资料: knowledge base and scanned documents behind one segmented header. */
    const val LIBRARY = "library?tab={tab}"
    fun library(tab: String = LIBRARY_TAB_KB) = "library?tab=$tab"
    const val LIBRARY_TAB_KB = "kb"
    const val LIBRARY_TAB_SCANS = "scans"
    const val PROFILE = "profile"

    /** Routes that show the shell's top and bottom bars. */
    val TAB_ROUTES = setOf(TIMELINE, CHAT, LIBRARY, PROFILE)

    // Sub-destinations
    const val CHAT_DETAIL = "chat/detail/{sessionId}?capture={capture}"
    /** [capture] opens the photo-question camera immediately (＋ → 拍题提问). */
    fun chatDetail(sessionId: Long, capture: Boolean = false) = "chat/detail/$sessionId?capture=$capture"

    const val SETTINGS = "settings"

    // Unified login (P8)
    const val LOGIN = "login?next={next}"
    fun login(next: String? = null) =
        if (next.isNullOrBlank()) "login?next=" else "login?next=${Uri.encode(next)}"

    // Scanner library detail screen — 现为统一文档编辑器(docId=0 表示新建)。

    const val SCANNER_DETAIL = "scanner/detail/{docId}"
    fun scannerDetail(docId: Long) = "scanner/detail/$docId"

    // Scanner per-page edit screen (Phase 3 Task 22).
    const val SCANNER_PAGE_EDIT = "scanner/page/{pageId}"
    fun scannerPageEdit(pageId: Long) = "scanner/page/$pageId"

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

    // Knowledge Base routes (P3)
    const val KB_DETAIL = "knowledge/detail/{entryId}"
    fun kbDetail(entryId: Long) = "knowledge/detail/$entryId"

    // Timeline (P4)
    const val TIMELINE_ADD_TASK = "timeline/add-task"
    const val TIMELINE_ADD_COURSE = "timeline/add-course"
    const val TIMELINE_DETAIL = "timeline/detail/{itemId}"
    fun timelineDetail(itemId: Long) = "timeline/detail/$itemId"
    const val TIMELINE_COURSE_LIST = "timeline/course-list"
    const val TIMELINE_COURSE_SERIES_EDIT = "timeline/course-series/{seriesId}"
    fun timelineCourseSeriesEdit(seriesId: Long) = "timeline/course-series/$seriesId"

    // BIT Import wizard (P5)
    const val IMPORT_WIZARD = "import"

    // 一键批量同步(首启自动 / 个中手动)
    const val SYNC_ALL = "sync-all?first={first}"
    fun syncAll(first: Boolean) = "sync-all?first=$first"

    // BIT 成绩查询 (P6)
    const val GRADES = "grades"
    const val GRADES_WHATIF = "grades/whatif"
    const val GRADES_SHARE = "grades/share"

    // Settings sub-pages (P4)
    const val SETTINGS_TIMETABLE = "settings/timetable"
    const val SETTINGS_NOTIF = "settings/notif"
    const val SETTINGS_LLM = "settings/llm"
    const val SETTINGS_AI_MODEL = "settings/ai-model"
    const val SETTINGS_GRADES_POLL = "settings/grades-poll"
    const val ASSIGNMENTS = "assignments"
    const val EXAMS = "exams"
    const val EMPTY_ROOM = "empty-room"
    const val SETTINGS_DDL_POLL = "settings/ddl-poll"
}
