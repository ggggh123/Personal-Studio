package com.example.personal_studio.ui

import android.graphics.PointF
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.personal_studio.feature.chat.ui.ChatDetailScreen
import com.example.personal_studio.feature.chat.ui.ChatListScreen
import com.example.personal_studio.feature.scanner.camera.CameraCaptureScreen
import com.example.personal_studio.feature.scanner.edge.EdgeDetectAndCropScreen
import com.example.personal_studio.feature.scanner.enhance.EnhanceReviewScreen
import com.example.personal_studio.feature.settings.ui.SettingsScreen
import com.example.personal_studio.ui.navigation.NavRoutes
import com.example.personal_studio.ui.placeholder.KnowledgePlaceholder
import com.example.personal_studio.ui.placeholder.ScannerPlaceholder
import com.example.personal_studio.ui.placeholder.TimelinePlaceholder
import java.io.File

@Composable
fun AppNavHost(navController: NavHostController) {
    val context = LocalContext.current
    NavHost(
        navController = navController,
        startDestination = NavRoutes.CHAT,
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
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.SCANNER) {
            ScannerPlaceholder(
                onStartSmokeCapture = { navController.navigate(NavRoutes.SCANNER_CAMERA) }
            )
        }
        composable(NavRoutes.KNOWLEDGE) { KnowledgePlaceholder() }
        composable(NavRoutes.TIMELINE) { TimelinePlaceholder() }

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

        composable(NavRoutes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
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
