package com.example.personal_studio.ui

import android.graphics.PointF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.personal_studio.feature.auth.ui.BitLoginScreen
import com.example.personal_studio.feature.bitimport.ImportEntryRoute
import com.example.personal_studio.feature.chat.ui.ChatDetailScreen
import com.example.personal_studio.feature.chat.ui.ChatListScreen
import com.example.personal_studio.feature.scanner.camera.CameraCaptureScreen
import com.example.personal_studio.feature.scanner.doc.PageEditScreen
import com.example.personal_studio.feature.scanner.edge.EdgeDetectAndCropScreen
import com.example.personal_studio.feature.scanner.enhance.EnhanceReviewScreen
import com.example.personal_studio.feature.scanner.library.ScanDocumentDetailScreen
import com.example.personal_studio.feature.scanner.library.ScanLibraryScreen
import com.example.personal_studio.feature.settings.ui.SettingsScreen
import com.example.personal_studio.ui.navigation.NavRoutes
import java.io.File

@Composable
fun AppNavHost(navController: NavHostController, startDestination: String = NavRoutes.CHAT) {
    val context = LocalContext.current
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(NavRoutes.CHAT) {
            ChatListScreen(
                onOpenSession = { sessionId ->
                    navController.navigate(NavRoutes.chatDetail(sessionId))
                }
            )
        }
        composable(
            route = NavRoutes.CHAT_DETAIL,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) { backStack ->
            val sessionId = backStack.arguments?.getLong("sessionId") ?: 0L
            ChatDetailScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onNavigateToKbEntry = { entryId -> navController.navigate(NavRoutes.kbDetail(entryId)) },
            )
        }

        composable(NavRoutes.SCANNER) {
            ScanLibraryScreen(
                onNewDoc = { navController.navigate(NavRoutes.scannerDetail(0L)) },
                onOpenDoc = { docId -> navController.navigate(NavRoutes.scannerDetail(docId)) },
            )
        }
        composable(
            route = NavRoutes.SCANNER_DETAIL,
            arguments = listOf(navArgument("docId") { type = NavType.LongType }),
        ) { backStack ->
            val docId = backStack.arguments?.getLong("docId") ?: return@composable
            ScanDocumentDetailScreen(
                docId = docId,
                onBack = { navController.popBackStack() },
                onOpenPage = { pageId -> navController.navigate(NavRoutes.scannerPageEdit(pageId)) },
                // TODO(P2 Phase 5 Task 26/27): wire share-pdf intent.
            )
        }
        composable(
            route = NavRoutes.SCANNER_PAGE_EDIT,
            arguments = listOf(navArgument("pageId") { type = NavType.LongType }),
        ) { backStack ->
            val pageId = backStack.arguments?.getLong("pageId") ?: return@composable
            PageEditScreen(
                pageId = pageId,
                onBack = { navController.popBackStack() },
                onNavigateToKbEntry = { entryId -> navController.navigate(NavRoutes.kbDetail(entryId)) },
            )
        }
        composable(NavRoutes.KNOWLEDGE) {
            com.example.personal_studio.feature.knowledge.ui.KbHomeScreen(
                onOpenEntry = { id -> navController.navigate(NavRoutes.kbDetail(id)) },
            )
        }
        composable(
            route = NavRoutes.KB_DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.LongType }),
        ) { backStack ->
            val id = backStack.arguments?.getLong("entryId") ?: return@composable
            com.example.personal_studio.feature.knowledge.ui.KbEntryDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenSource = { entry ->
                    when (entry.source) {
                        com.example.personal_studio.domain.model.KbSource.CHAT_MESSAGE,
                        com.example.personal_studio.domain.model.KbSource.CHAT_SESSION ->
                            entry.sourceChatSessionId?.let { sid ->
                                navController.navigate(NavRoutes.chatDetail(sid))
                            }
                        com.example.personal_studio.domain.model.KbSource.SCAN ->
                            // Phase 5 (Task 35) wires real scan-page-from-id navigation.
                            // For now jump to scanner library so user has a destination.
                            navController.navigate(NavRoutes.SCANNER)
                    }
                },
                onOpenRelated = { relId -> navController.navigate(NavRoutes.kbDetail(relId)) },
            )
        }
        composable(NavRoutes.TIMELINE) {
            com.example.personal_studio.feature.timeline.ui.TimelineScreen(
                onAddTask = { navController.navigate(NavRoutes.TIMELINE_ADD_TASK) },
                onAddCourse = { navController.navigate(NavRoutes.TIMELINE_ADD_COURSE) },
                onOpenDetail = { id -> navController.navigate(NavRoutes.timelineDetail(id)) },
                onOpenWeekGrid = { navController.navigate(NavRoutes.TIMELINE_WEEK_GRID) },
            )
        }
        composable(NavRoutes.PROFILE) {
            com.example.personal_studio.feature.profile.ui.ProfileScreen(
                onOpenTimetable = { navController.navigate(NavRoutes.TIMELINE_WEEK_GRID) },
                onOpenGrades = { navController.navigate(NavRoutes.GRADES) },
                onOpenAssignments = { navController.navigate(NavRoutes.ASSIGNMENTS) },
                onOpenExams = { navController.navigate(NavRoutes.EXAMS) },
                onOpenGradesPoll = { navController.navigate(NavRoutes.SETTINGS_GRADES_POLL) },
                onOpenDdlPoll = { navController.navigate(NavRoutes.SETTINGS_DDL_POLL) },
                onOpenEmptyRoom = { navController.navigate(NavRoutes.EMPTY_ROOM) },
                onLogin = { navController.navigate(NavRoutes.login(null)) },
            )
        }
        composable(NavRoutes.TIMELINE_WEEK_GRID) {
            com.example.personal_studio.feature.timeline.ui.CourseWeekGridScreen(
                onBack = { navController.popBackStack() },
                onOpenItem = { id -> navController.navigate(NavRoutes.timelineDetail(id)) },
                onNavigateToImport = { navController.navigate(NavRoutes.IMPORT_WIZARD) },
                onNavigateToAddCourse = { navController.navigate(NavRoutes.TIMELINE_ADD_COURSE) },
            )
        }
        composable(NavRoutes.TIMELINE_ADD_TASK) {
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { /* result handled by banner state */ }
            com.example.personal_studio.feature.timeline.ui.AddTaskScreen(
                onSaved = { _ -> navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onRequestNotifPermission = {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }
        composable(NavRoutes.TIMELINE_ADD_COURSE) {
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) {}
            com.example.personal_studio.feature.timeline.ui.AddCourseScreen(
                onBack = { navController.popBackStack() },
                onRequestNotifPermission = {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }
        composable(
            route = NavRoutes.TIMELINE_DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "personalstudio://timeline/detail/{itemId}"
                }
            ),
        ) { backStack ->
            val itemId = backStack.arguments?.getLong("itemId") ?: return@composable
            com.example.personal_studio.feature.timeline.ui.TaskDetailScreen(
                itemId = itemId,
                onBack = { navController.popBackStack() },
                onOpenCourseSeries = { sid -> navController.navigate(NavRoutes.timelineCourseSeriesEdit(sid)) },
            )
        }
        composable(NavRoutes.TIMELINE_COURSE_LIST) {
            com.example.personal_studio.feature.timeline.ui.CourseSeriesListScreen(
                onBack = { navController.popBackStack() },
                onOpenSeries = { sid -> navController.navigate(NavRoutes.timelineCourseSeriesEdit(sid)) },
                onAddCourse = { navController.navigate(NavRoutes.TIMELINE_ADD_COURSE) },
            )
        }
        composable(
            route = NavRoutes.TIMELINE_COURSE_SERIES_EDIT,
            arguments = listOf(navArgument("seriesId") { type = NavType.LongType }),
        ) { backStack ->
            val sid = backStack.arguments?.getLong("seriesId") ?: return@composable
            com.example.personal_studio.feature.timeline.ui.CourseSeriesEditScreen(
                seriesId = sid,
                onBack = { navController.popBackStack() },
            )
        }
        composable(NavRoutes.IMPORT_WIZARD) {
            ImportEntryRoute(
                onClose = { navController.popBackStack() },
                onNeedLogin = {
                    navController.navigate(NavRoutes.login("import")) {
                        popUpTo(NavRoutes.IMPORT_WIZARD) { inclusive = true }
                    }
                },
            )
        }
        composable(
            NavRoutes.GRADES,
            deepLinks = listOf(navDeepLink { uriPattern = "personalstudio://grades" }),
        ) {
            com.example.personal_studio.feature.bitgrades.ui.GradesScreen(
                onBack = { navController.popBackStack() },
                onNeedLogin = { navController.navigate(NavRoutes.login("grades")) },
                onOpenChat = { sid -> navController.navigate(NavRoutes.chatDetail(sid)) },
                onOpenWhatIf = { navController.navigate(NavRoutes.GRADES_WHATIF) },
                onOpenShare = { navController.navigate(NavRoutes.GRADES_SHARE) },
            )
        }
        composable(NavRoutes.GRADES_WHATIF) {
            com.example.personal_studio.feature.bitgrades.ui.WhatIfScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(NavRoutes.GRADES_SHARE) {
            com.example.personal_studio.feature.bitgrades.ui.ShareCardScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // --- Scanner single-page smoke flow (Task 14) ---
        composable(NavRoutes.SCANNER_CAMERA) {
            val tmpDir = File(context.filesDir, "scans/tmp").apply { mkdirs() }
            CameraCaptureScreen(
                outputDir = tmpDir,
                onCaptured = { file, autoDetect, liveNorm ->
                    val liveEncoded = liveNorm?.let(::encodeCorners).orEmpty()
                    navController.navigate(
                        NavRoutes.scannerEdge(file.absolutePath, autoDetect, liveEncoded),
                    ) {
                        popUpTo(NavRoutes.SCANNER_CAMERA) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = NavRoutes.SCANNER_EDGE,
            arguments = listOf(
                navArgument("tmp") { type = NavType.StringType },
                navArgument("auto") { type = NavType.BoolType; defaultValue = true },
                navArgument("live") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStack ->
            val tmp = Uri.decode(backStack.arguments?.getString("tmp") ?: "")
            val auto = backStack.arguments?.getBoolean("auto") ?: true
            val liveStr = Uri.decode(backStack.arguments?.getString("live") ?: "")
            val liveNorm = if (liveStr.isBlank()) null else decodeCorners(liveStr)
            EdgeDetectAndCropScreen(
                capturedFilePath = tmp,
                autoDetect = auto,
                preDetectedNormalized = liveNorm,
                onConfirm = { corners ->
                    val encoded = encodeCorners(corners)
                    navController.navigate(NavRoutes.scannerEnhance(tmp, encoded)) {
                        popUpTo(NavRoutes.SCANNER_EDGE) { inclusive = true }
                    }
                },
                onRetake = { navController.popBackStack() },
            )
        }
        composable(
            route = NavRoutes.SCANNER_ENHANCE,
            arguments = listOf(
                navArgument("tmp") { type = NavType.StringType },
                navArgument("corners") { type = NavType.StringType },
            ),
        ) { backStack ->
            val tmp = Uri.decode(backStack.arguments?.getString("tmp") ?: "")
            val cornersStr = Uri.decode(backStack.arguments?.getString("corners") ?: "")
            val corners = decodeCorners(cornersStr)
            EnhanceReviewScreen(
                capturedFilePath = tmp,
                cornersBitmapPx = corners,
                onConfirm = { _, _ -> navController.popBackStack(NavRoutes.SCANNER, inclusive = false) },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(
            route = NavRoutes.LOGIN,
            arguments = listOf(navArgument("next") { type = NavType.StringType; defaultValue = "" }),
        ) { backStack ->
            val next = backStack.arguments?.getString("next").orEmpty().ifBlank { null }?.let { Uri.decode(it) }
            BitLoginScreen(
                skipVisible = next == null,
                onSucceeded = {
                    if (next != null) navController.navigate(next) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                        launchSingleTop = true   // 守卫场景目标屏可能已在栈中,避免压重复实例
                    } else navController.navigate(NavRoutes.PROFILE) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSkipped = {
                    navController.navigate(NavRoutes.PROFILE) { popUpTo(NavRoutes.LOGIN) { inclusive = true } }
                },
            )
        }

        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavRoutes.SETTINGS_TIMETABLE) {
            com.example.personal_studio.feature.settings.ui.TimetableEditorScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.SETTINGS_SEMESTER) {
            com.example.personal_studio.feature.settings.ui.SemesterSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.SETTINGS_NOTIF) {
            com.example.personal_studio.feature.settings.ui.NotifSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            NavRoutes.SETTINGS_GRADES_POLL,
            deepLinks = listOf(navDeepLink { uriPattern = "personalstudio://settings/grades-poll" }),
        ) {
            com.example.personal_studio.feature.settings.ui.GradesPollSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            NavRoutes.ASSIGNMENTS,
            deepLinks = listOf(navDeepLink { uriPattern = "personalstudio://assignments" }),
        ) {
            com.example.personal_studio.feature.bitddl.ui.AssignmentsScreen(
                onBack = { navController.popBackStack() },
                onNeedLogin = { navController.navigate(NavRoutes.login("assignments")) },
            )
        }
        composable(
            NavRoutes.EXAMS,
            deepLinks = listOf(navDeepLink { uriPattern = "personalstudio://exams" }),
        ) {
            com.example.personal_studio.feature.bitexam.ui.ExamsScreen(
                onBack = { navController.popBackStack() },
                onNeedLogin = { navController.navigate(NavRoutes.login("exams")) },
            )
        }
        composable(
            NavRoutes.EMPTY_ROOM,
            deepLinks = listOf(navDeepLink { uriPattern = "personalstudio://empty-room" }),
        ) {
            com.example.personal_studio.feature.emptyroom.ui.EmptyRoomScreen(
                onBack = { navController.popBackStack() },
                onNeedLogin = { navController.navigate(NavRoutes.login("empty-room")) },
            )
        }
        composable(
            NavRoutes.SETTINGS_DDL_POLL,
            deepLinks = listOf(navDeepLink { uriPattern = "personalstudio://settings/ddl-poll" }),
        ) {
            com.example.personal_studio.feature.settings.ui.DdlPollSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** Encode 4 corners as "x1,y1;x2,y2;x3,y3;x4,y4". Smoke-only helper. */
private fun encodeCorners(corners: List<PointF>): String =
    corners.joinToString(";") { "${it.x},${it.y}" }

private fun decodeCorners(s: String): List<PointF> =
    s.split(";").mapNotNull { pair ->
        val parts = pair.split(",")
        if (parts.size == 2) PointF(parts[0].toFloat(), parts[1].toFloat()) else null
    }
