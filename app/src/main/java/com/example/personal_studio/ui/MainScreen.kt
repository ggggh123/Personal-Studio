package com.example.personal_studio.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.personal_studio.ui.components.TerminalBottomBar
import com.example.personal_studio.ui.components.TerminalSplash
import com.example.personal_studio.ui.components.TerminalTab
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.navigation.NavRoutes
import com.example.personal_studio.ui.theme.Void
import com.example.personal_studio.ui.theme.scanLines
import com.example.personal_studio.ui.theme.vignette
import kotlinx.coroutines.delay

private val tabs = listOf(
    TerminalTab(NavRoutes.CHAT, "chat", Icons.Filled.Terminal),
    TerminalTab(NavRoutes.SCANNER, "scan", Icons.Filled.CameraAlt),
    TerminalTab(NavRoutes.PROFILE, "我", Icons.Filled.Person, prominent = true),
    TerminalTab(NavRoutes.KNOWLEDGE, "kb", Icons.AutoMirrored.Filled.MenuBook),
    TerminalTab(NavRoutes.TIMELINE, "day", Icons.Filled.CalendarMonth),
)

/** 冷启动品牌屏的最短停留(ms);实际停留 = max(此值, 起始目的地解析时间)。 */
private const val SPLASH_MIN_MS = 1300L

@Composable
fun MainScreen(navController: NavHostController = rememberNavController()) {
    val rootVm: com.example.personal_studio.feature.auth.RootViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()
    val startDest by rootVm.startDestination.collectAsStateWithLifecycle()

    // 每次冷启动先驻留品牌屏 SPLASH_MIN_MS,再 crossfade 渐变进入。
    var minElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPLASH_MIN_MS)
        minElapsed = true
    }

    Crossfade(
        targetState = (!minElapsed || startDest == null),
        animationSpec = tween(durationMillis = 450),
        label = "boot-splash",
    ) { showSplash ->
        if (showSplash) {
            TerminalSplash()
        } else {
            startDest?.let { dest -> MainShell(navController = navController, startDestination = dest) }
        }
    }
}

@Composable
private fun MainShell(navController: NavHostController, startDestination: String) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isTabRoute = tabs.any { it.route == currentRoute }
    val currentTab = tabs.firstOrNull { it.route == currentRoute }
    // Scanner sub-routes show camera/image content that must not be tinted by
    // the CRT phosphor glow or dimmed by the vignette — the scanlines overlay
    // was painting a greenish film on top of the preview and looked hazy.
    val isScannerSubRoute = currentRoute?.startsWith("scanner/") == true

    // Sub-routes (chat detail, settings) render their own top bar — we only show
    // the shell's chrome on tab destinations. `contentWindowInsets = WindowInsets(0)`
    // lets each screen own its safe-area padding (via TerminalTopBar / input row),
    // avoiding doubled status/navigation bar gaps.
    Scaffold(
        containerColor = Void,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (isTabRoute) {
                TerminalTopBar(
                    route = currentTab?.route ?: "home",
                    trailing = {
                        IconButton(
                            onClick = {
                                navController.navigate(NavRoutes.SETTINGS) {
                                    launchSingleTop = true
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = "settings")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (isTabRoute) {
                TerminalBottomBar(
                    tabs = tabs,
                    selectedRoute = currentRoute,
                    onTabClick = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(NavRoutes.PROFILE) { saveState = true }
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
                .let { if (isScannerSubRoute) it else it.scanLines().vignette() },
        ) {
            AppNavHost(navController = navController, startDestination = startDestination)
        }
    }
}
