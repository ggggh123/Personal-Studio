package com.example.personal_studio.ui.navigation

import android.net.Uri

object NavRoutes {
    // Bottom-nav tabs
    const val CHAT = "chat"
    const val SCANNER = "scanner"
    const val KNOWLEDGE = "knowledge"
    const val TIMELINE = "timeline"
    const val PROFILE = "profile"

    // Sub-destinations
    const val CHAT_DETAIL = "chat/detail/{sessionId}"
    fun chatDetail(sessionId: Long) = "chat/detail/$sessionId"

    const val SETTINGS = "settings"

    // Unified login (P8)
    const val LOGIN = "login?next={next}"
    fun login(next: String? = null) =
        if (next.isNullOrBlank()) "login?next=" else "login?next=${Uri.encode(next)}"

    // Scanner multi-page flow host (Phase 3).
    const val SCANNER_NEW_DOC = "scanner/new?resumeDocId={resumeDocId}"
    fun scannerNewDoc(resumeDocId: Long? = null) =
        if (resumeDocId == null) "scanner/new?resumeDocId=" else "scanner/new?resumeDocId=$resumeDocId"

    // Scanner library detail screen (Phase 3 Task 21).
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
    const val TIMELINE_WEEK_GRID = "timeline/week-grid"

    // BIT Import wizard (P5)
    const val IMPORT_WIZARD = "import"

    // BIT 成绩查询 (P6)
    const val GRADES = "grades"
    const val GRADES_WHATIF = "grades/whatif"
    const val GRADES_SHARE = "grades/share"

    // Settings sub-pages (P4)
    const val SETTINGS_TIMETABLE = "settings/timetable"
    const val SETTINGS_SEMESTER = "settings/semester"
    const val SETTINGS_NOTIF = "settings/notif"
    const val SETTINGS_GRADES_POLL = "settings/grades-poll"
    const val ASSIGNMENTS = "assignments"
    const val EXAMS = "exams"
    const val EMPTY_ROOM = "empty-room"
    const val SETTINGS_DDL_POLL = "settings/ddl-poll"
}
