package com.example.personal_studio.ui

import android.graphics.PointF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
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
import com.example.personal_studio.feature.library.LibraryScreen
import com.example.personal_studio.feature.settings.ui.SettingsScreen
import com.example.personal_studio.ui.navigation.NavRoutes
import com.example.personal_studio.ui.theme.TgMotion
import java.io.File

/** Switching between bottom-bar tabs crossfades; everything else slides like a page. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in NavRoutes.TAB_ROUTES && targetState.destination.route in NavRoutes.TAB_ROUTES

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = NavRoutes.TIMELINE,
    onOpenSync: (start: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    NavHost(
        navController = navController,
        startDestination = startDestination,
        // Pages enter from the right with the standard spring and leave faster; back reverses
        // it. On Android 14+ the system back gesture scrubs these pop transitions (predictive back).
        enterTransition = {
            if (isTabSwitch()) fadeIn(tween(220, delayMillis = 60))
            else slideInHorizontally(TgMotion.standard<IntOffset>()) { it / 4 } + fadeIn(tween(220))
        },
        exitTransition = {
            if (isTabSwitch()) fadeOut(tween(120))
            else slideOutHorizontally(TgMotion.standard<IntOffset>()) { -it / 10 } + fadeOut(tween(200))
        },
        popEnterTransition = {
            if (isTabSwitch()) fadeIn(tween(220, delayMillis = 60))
            else slideInHorizontally(TgMotion.standard<IntOffset>()) { -it / 10 } + fadeIn(tween(220))
        },
        popExitTransition = {
            if (isTabSwitch()) fadeOut(tween(120))
            else slideOutHorizontally(TgMotion.exit<IntOffset>()) { it / 4 } + fadeOut(tween(200))
        },
    ) {
        composable(NavRoutes.CHAT) {
            ChatListScreen(
                onOpenSession = { sessionId -> navController.navigate(NavRoutes.chatDetail(sessionId)) },
                onPhotoQuestion = { sessionId -> navController.navigate(NavRoutes.chatDetail(sessionId, capture = true)) },
            )
        }
        composable(
            route = NavRoutes.CHAT_DETAIL,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType },
                navArgument("capture") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { backStack ->
            val sessionId = backStack.arguments?.getLong("sessionId") ?: 0L
            ChatDetailScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onNavigateToKbEntry = { entryId -> navController.navigate(NavRoutes.kbDetail(entryId)) },
                startWithQuickCapture = backStack.arguments?.getBoolean("capture") ?: false,
            )
        }

        composable(
            route = NavRoutes.LIBRARY,
            arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = NavRoutes.LIBRARY_TAB_KB }),
        ) { backStack ->
            LibraryScreen(
                initialTab = backStack.arguments?.getString("tab") ?: NavRoutes.LIBRARY_TAB_KB,
                onOpenEntry = { id -> navController.navigate(NavRoutes.kbDetail(id)) },
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
        composable(
            route = NavRoutes.KB_DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.LongType }),
        ) { backStack ->
            val id = backStack.arguments?.getLong("entryId") ?: return@composable
            com.example.personal_studio.feature.knowledge.ui.KbEntryDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenSource = { target ->
                    when (target) {
                        is com.example.personal_studio.feature.knowledge.vm.KbSourceTarget.Chat ->
                            navController.navigate(NavRoutes.chatDetail(target.sessionId))
                        is com.example.personal_studio.feature.knowledge.vm.KbSourceTarget.ScanPage ->
                            navController.navigate(NavRoutes.scannerPageEdit(target.pageId))
                    }
                },
                onOpenRelated = { relId -> navController.navigate(NavRoutes.kbDetail(relId)) },
            )
        }
        composable(NavRoutes.TIMELINE) {
            com.example.personal_studio.feature.timeline.ui.TimelineScreen(
                onOpenSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onOpenSync = { onOpenSync(false) },
                onOpenCourseSeries = { sid -> navController.navigate(NavRoutes.timelineCourseSeriesEdit(sid)) },
            )
        }
        composable(NavRoutes.PROFILE) {
            com.example.personal_studio.feature.profile.ui.CampusScreen(
                onOpenGrades = { navController.navigate(NavRoutes.GRADES) },
                onOpenAssignments = { navController.navigate(NavRoutes.ASSIGNMENTS) },
                onOpenExams = { navController.navigate(NavRoutes.EXAMS) },
                onOpenEmptyRoom = { navController.navigate(NavRoutes.EMPTY_ROOM) },
                onLogin = { navController.navigate(NavRoutes.login(null)) },
                onOpenSync = onOpenSync,
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
                onImport = { navController.navigate(NavRoutes.IMPORT_WIZARD) },
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
                onConfirm = { _, _ -> navController.popBackStack() },
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
                onSucceeded = { firstSyncPending ->
                    val dest = when {
                        next != null -> next
                        firstSyncPending -> NavRoutes.syncAll(first = true)
                        else -> NavRoutes.TIMELINE
                    }
                    navController.navigate(dest) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                        launchSingleTop = true   // 守卫场景目标屏可能已在栈中,避免压重复实例
                    }
                },
                onSkipped = {
                    navController.navigate(NavRoutes.TIMELINE) { popUpTo(NavRoutes.LOGIN) { inclusive = true } }
                },
            )
        }

        composable(
            route = NavRoutes.SYNC_ALL,
            arguments = listOf(navArgument("first") { type = NavType.BoolType; defaultValue = false }),
        ) { backStack ->
            val first = backStack.arguments?.getBoolean("first") ?: false
            com.example.personal_studio.feature.bitimport.ui.SyncAllScreen(
                onFinish = {
                    if (first) navController.navigate(NavRoutes.TIMELINE) {
                        popUpTo(NavRoutes.SYNC_ALL) { inclusive = true }
                        launchSingleTop = true
                    } else navController.popBackStack()
                },
                onLogin = { navController.navigate(NavRoutes.login(null)) },
            )
        }

        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavRoutes.SETTINGS_AI_MODEL) {
            com.example.personal_studio.feature.settings.ui.AiModelScreen(
                onBack = { navController.popBackStack() },
                onOpenAdvanced = { navController.navigate(NavRoutes.SETTINGS_LLM) },
            )
        }
        composable(NavRoutes.SETTINGS_LLM) {
            com.example.personal_studio.feature.settings.ui.LlmSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(NavRoutes.SETTINGS_TIMETABLE) {
            com.example.personal_studio.feature.settings.ui.TimetableEditorScreen(onBack = { navController.popBackStack() })
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
