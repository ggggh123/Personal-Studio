package com.example.personal_studio.feature.knowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.knowledge.ui.components.KbMistakeRow
import com.example.personal_studio.feature.knowledge.vm.KbMistakesViewModel
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Void

/**
 * Mistakes (错题集) sub-screen — flat list of all entries with a non-null
 * standardizedQuestion, image-thumbnail-prominent rows.
 *
 * Reached from KbHomeScreen's [mistakes N] chip. Has its own TerminalTopBar +
 * back button because it's a sub-route, not a tab destination — MainScreen's
 * Scaffold doesn't render the shell topbar for non-tab routes.
 */
@Composable
fun KbMistakesScreen(onBack: () -> Unit, onOpenEntry: (Long) -> Unit) {
    val vm: KbMistakesViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Void)) {
        Column(Modifier.fillMaxSize()) {
            TerminalTopBar(
                route = "kb/mistakes/",
                trailing = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back", tint = Foam)
                    }
                },
            )
            Text(
                "$ ls mistakes/    ${state.entries.size} entries",
                color = FoamDim,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.entries, key = { it.id }) { e -> KbMistakeRow(e, onOpenEntry) }
            }
        }
    }
}
