package com.example.personal_studio.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.personal_studio.feature.settings.ui.SettingsScreen
import com.example.personal_studio.ui.navigation.NavRoutes
import com.example.personal_studio.ui.placeholder.ChatPlaceholder
import com.example.personal_studio.ui.placeholder.KnowledgePlaceholder
import com.example.personal_studio.ui.placeholder.ScannerPlaceholder
import com.example.personal_studio.ui.placeholder.TimelinePlaceholder

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.CHAT,
    ) {
        composable(NavRoutes.CHAT) { ChatPlaceholder() }
        composable(NavRoutes.SCANNER) { ScannerPlaceholder() }
        composable(NavRoutes.KNOWLEDGE) { KnowledgePlaceholder() }
        composable(NavRoutes.TIMELINE) { TimelinePlaceholder() }

        composable(NavRoutes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
