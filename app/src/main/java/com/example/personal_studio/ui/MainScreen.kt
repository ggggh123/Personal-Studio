package com.example.personal_studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.personal_studio.ui.components.TerminalBottomBar
import com.example.personal_studio.ui.components.TerminalTab
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.navigation.NavRoutes
import com.example.personal_studio.ui.theme.Void
import com.example.personal_studio.ui.theme.scanLines
import com.example.personal_studio.ui.theme.vignette

private val tabs = listOf(
    TerminalTab(NavRoutes.CHAT, "chat", Icons.Filled.Terminal),
    TerminalTab(NavRoutes.SCANNER, "scan", Icons.Filled.CameraAlt),
    TerminalTab(NavRoutes.KNOWLEDGE, "kb", Icons.AutoMirrored.Filled.MenuBook),
    TerminalTab(NavRoutes.TIMELINE, "day", Icons.Filled.CalendarMonth),
)

@Composable
fun MainScreen(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val currentTab = tabs.firstOrNull { it.route == currentRoute }

    Scaffold(
        containerColor = Void,
        topBar = {
            TerminalTopBar(
                route = currentTab?.route ?: (currentRoute ?: "home"),
                trailing = {
                    IconButton(onClick = { navController.navigate(NavRoutes.SETTINGS) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "settings")
                    }
                }
            )
        },
        bottomBar = {
            // Hide bottom bar on non-tab destinations (e.g. Settings)
            if (tabs.any { it.route == currentRoute }) {
                TerminalBottomBar(
                    tabs = tabs,
                    selectedRoute = currentRoute,
                    onTabClick = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(NavRoutes.CHAT) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .background(Void)
                .scanLines()
                .vignette(),
        ) {
            AppNavHost(navController = navController)
        }
    }
}
