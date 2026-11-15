package com.example.personal_studio.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.feature.knowledge.ui.KbHomeScreen
import com.example.personal_studio.feature.scanner.library.ScanLibraryScreen
import com.example.personal_studio.ui.components.TgSegmented
import com.example.personal_studio.ui.navigation.NavRoutes

/**
 * 资料: the knowledge base and the scanned documents, one tab with a segmented header.
 * Both lists keep their existing screens until batch 3 redesigns them.
 */
@Composable
fun LibraryScreen(
    initialTab: String,
    onOpenEntry: (Long) -> Unit,
    onNewDoc: () -> Unit,
    onOpenDoc: (Long) -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(if (initialTab == NavRoutes.LIBRARY_TAB_SCANS) 1 else 0) }
    Column(Modifier.fillMaxSize()) {
        TgSegmented(
            options = listOf("知识库", "扫描文档"),
            selectedIndex = selected,
            onSelect = { selected = it },
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp),
        )
        AnimatedContent(
            targetState = selected,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "library",
        ) { tab ->
            Box(Modifier.fillMaxSize()) {
                if (tab == 0) {
                    KbHomeScreen(onOpenEntry = onOpenEntry)
                } else {
                    ScanLibraryScreen(onNewDoc = onNewDoc, onOpenDoc = onOpenDoc)
                }
            }
        }
    }
}
