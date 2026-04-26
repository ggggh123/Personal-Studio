package com.example.personal_studio.feature.knowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.knowledge.ui.components.CategoryChipRow
import com.example.personal_studio.feature.knowledge.ui.components.KbEntryRow
import com.example.personal_studio.feature.knowledge.ui.components.KbSearchBar
import com.example.personal_studio.feature.knowledge.vm.KbHomeViewModel
import com.example.personal_studio.ui.placeholder.KnowledgePlaceholder
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

/**
 * KB tab default landing. Hosts KbHomeViewModel; renders search bar + counts row +
 * category chips + entries list. The list shows the rescued OR-mode results (when
 * present) instead of the strict AND results.
 *
 * Phase 4 baseline. Phase 6 / Task 44 wires SearchKbUseCase for the AND→OR rescue
 * trigger that replaces the current best-effort behavior.
 */
@Composable
fun KbHomeScreen(
    onOpenEntry: (Long) -> Unit,
    onOpenMistakes: () -> Unit,
) {
    val vm: KbHomeViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val displayedEntries = state.rescuedEntries ?: state.entries

    Box(Modifier.fillMaxSize().background(Void)) {
        Column(Modifier.fillMaxSize()) {
            KbSearchBar(query = state.searchQuery, onQueryChange = vm::onSearchChange)

            // Top stats row: [notes N] [mistakes N]
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatChip("notes", state.notesCount, selected = state.showNotes) { vm.onToggleNotes() }
                Spacer(Modifier.width(12.dp))
                StatChip("mistakes", state.mistakesCount, selected = false) { onOpenMistakes() }
            }

            CategoryChipRow(
                items = state.categories,
                selectedId = state.selectedCategoryId,
                onSelect = vm::onSelectCategory,
            )

            Text(
                "─────────── ${if (state.isSearching) "matches" else "recent"} ───────────",
                color = FoamDim,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp),
            )

            if (displayedEntries.isEmpty()) {
                KnowledgePlaceholder()
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(displayedEntries, key = { it.id }) { e ->
                        KbEntryRow(entry = e, onClick = onOpenEntry)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Phosphor else Foam
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = color)) { append("[$label] ") }
            withStyle(SpanStyle(color = if (selected) Phosphor else FoamDim)) { append(count.toString()) }
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp),
    )
}
