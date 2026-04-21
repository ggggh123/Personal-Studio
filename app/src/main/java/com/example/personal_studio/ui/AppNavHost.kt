package com.example.personal_studio.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.personal_studio.feature.chat.ui.ChatDetailScreen
import com.example.personal_studio.feature.chat.ui.ChatListScreen
import com.example.personal_studio.feature.settings.ui.SettingsScreen
import com.example.personal_studio.ui.navigation.NavRoutes
import com.example.personal_studio.ui.placeholder.KnowledgePlaceholder
import com.example.personal_studio.ui.placeholder.ScannerPlaceholder
import com.example.personal_studio.ui.placeholder.TimelinePlaceholder

@Composable
fun AppNavHost(navController: NavHostController) {
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

        composable(NavRoutes.SCANNER) { ScannerPlaceholder() }
        composable(NavRoutes.KNOWLEDGE) { KnowledgePlaceholder() }
        composable(NavRoutes.TIMELINE) { TimelinePlaceholder() }

        composable(NavRoutes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
