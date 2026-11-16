package com.example.personal_studio.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.personal_studio.feature.auth.RootViewModel
import com.example.personal_studio.feature.bitimport.ui.SyncAllViewModel
import com.example.personal_studio.feature.bitimport.ui.SyncSheet
import com.example.personal_studio.feature.timeline.today.QuickAddTaskSheet
import com.example.personal_studio.feature.timeline.today.QuickAddTaskViewModel
import com.example.personal_studio.ui.navigation.NavRoutes
import com.example.personal_studio.ui.shell.LocalShellOverlay
import com.example.personal_studio.ui.shell.LocalShellToast
import com.example.personal_studio.ui.shell.NewItemSheet
import com.example.personal_studio.ui.shell.ShellOverlay
import com.example.personal_studio.ui.shell.ShellOverlayHost
import com.example.personal_studio.ui.shell.ShellToast
import com.example.personal_studio.ui.shell.ShellToastHost
import com.example.personal_studio.ui.shell.ShellViewModel
import com.example.personal_studio.ui.shell.TgBottomBar
import com.example.personal_studio.ui.shell.TgTabs
import com.example.personal_studio.ui.shell.TgTopBar
import com.example.personal_studio.ui.theme.LocalThemeSafePoint
import com.example.personal_studio.ui.theme.Tg

@Composable
fun MainScreen(navController: NavHostController = rememberNavController()) {
    val rootVm: RootViewModel = hiltViewModel()
    val startDest by rootVm.startDestination.collectAsStateWithLifecycle()

    // No branded splash: MainActivity holds the first frame until the start destination is
    // known (a single DataStore read), so this background is rarely visible at all.
    Box(Modifier.fillMaxSize().background(Tg.colors.bg)) {
        startDest?.let { dest -> MainShell(navController = navController, startDestination = dest) }
    }
}

@Composable
private fun MainShell(navController: NavHostController, startDestination: String) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isTabRoute = currentRoute in NavRoutes.TAB_ROUTES
    val shellVm: ShellViewModel = hiltViewModel()
    val quickAddVm: QuickAddTaskViewModel = hiltViewModel()
    val quickAdd by quickAddVm.ui.collectAsStateWithLifecycle()
    var plusOpen by rememberSaveable { mutableStateOf(false) }
    var quickAddOpen by rememberSaveable { mutableStateOf(false) }
    val syncVm: SyncAllViewModel = hiltViewModel()
    val syncProgress by syncVm.progress.collectAsStateWithLifecycle()
    val lastSyncAt by syncVm.lastSyncAt.collectAsStateWithLifecycle(null)
    var syncOpen by rememberSaveable { mutableStateOf(false) }
    val overlay = remember { ShellOverlay() }
    val toast = remember { ShellToast() }
    val context = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // Navigating is a safe moment to apply a pending light/dark switch of the 天光 theme.
    val themeSafePoint = LocalThemeSafePoint.current
    LaunchedEffect(currentRoute) {
        themeSafePoint()
        if (!isTabRoute) {
            plusOpen = false
            quickAddOpen = false
            syncOpen = false
        }
    }

    fun go(route: String) {
        plusOpen = false
        navController.navigate(route)
    }

    fun openTab(route: String) {
        plusOpen = false
        val target = if (route == NavRoutes.LIBRARY) NavRoutes.library() else route
        navController.navigate(target) {
            // 今日 is the root after login or skip, so tab state is saved against it.
            popUpTo(NavRoutes.TIMELINE) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalShellOverlay provides overlay, LocalShellToast provides toast) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(Tg.colors.bg)) {
                Box(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxSize()) {
                        // 今日 draws its own header (date, tagline, horizon).
                        if (isTabRoute && currentRoute != NavRoutes.TIMELINE) {
                            TgTopBar(
                                title = TgTabs.firstOrNull { it.route == currentRoute }?.label.orEmpty(),
                                onOpenSettings = { go(NavRoutes.SETTINGS) },
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            // No SharedTransitionLayout (or any lookahead scope) around the host:
                            // with Compose 1.7.0 it crashed the chat list on scroll ("Placement
                            // happened before lookahead") when the formula WebView rows were reused.
                            // Card → detail uses its own container transform (MorphSheet) instead.
                            AppNavHost(
                                navController = navController,
                                startDestination = startDestination,
                                onOpenSync = { start ->
                                    if (start) syncVm.startAll()
                                    syncOpen = true
                                },
                            )
                        }
                    }
                    if (isTabRoute) {
                        NewItemSheet(
                            visible = plusOpen,
                            onDismiss = { plusOpen = false },
                            onScan = { go(NavRoutes.scannerDetail(0L)) },
                            onPhotoQuestion = {
                                plusOpen = false
                                shellVm.newChatSession { id -> navController.navigate(NavRoutes.chatDetail(id, capture = true)) }
                            },
                            onNewTask = {
                                plusOpen = false
                                quickAddVm.reset()
                                quickAddOpen = true
                            },
                            onNewCourse = { go(NavRoutes.TIMELINE_ADD_COURSE) },
                            bottomInset = 0.dp,
                        )
                    }
                }
                if (isTabRoute) {
                    TgBottomBar(
                        selectedRoute = currentRoute,
                        plusOpen = plusOpen,
                        onTabClick = { openTab(it.route) },
                        onPlusClick = { plusOpen = !plusOpen },
                    )
                }
            }
            // Above everything, bottom bar included: page sheets, the quick task sheet, toasts.
            ShellOverlayHost(overlay)
            QuickAddTaskSheet(
                visible = quickAddOpen,
                state = quickAdd,
                onTitle = quickAddVm::onTitle,
                onDue = quickAddVm::onDue,
                onSave = {
                    quickAddVm.save {
                        quickAddOpen = false
                        openTab(NavRoutes.TIMELINE)
                        toast.show("已添加，截止前会提醒你")
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                },
                onMore = {
                    quickAddOpen = false
                    go(NavRoutes.TIMELINE_ADD_TASK)
                },
                onDismiss = { quickAddOpen = false },
            )
            SyncSheet(
                visible = syncOpen,
                progress = syncProgress,
                running = syncProgress?.let { !it.done } == true,
                lastSyncAt = lastSyncAt,
                now = System.currentTimeMillis(),
                onStart = syncVm::startAll,
                onRetryFailed = syncVm::retryFailed,
                onLogin = {
                    syncOpen = false
                    go(NavRoutes.login(null))
                },
                onDismiss = { syncOpen = false },
            )
            ShellToastHost(toast, bottomPadding = if (isTabRoute) 80.dp else 24.dp)
        }
    }
}
