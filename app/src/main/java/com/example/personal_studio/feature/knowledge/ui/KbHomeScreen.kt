package com.example.personal_studio.feature.knowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.personal_studio.feature.knowledge.vm.KbHomeFilter
import com.example.personal_studio.feature.knowledge.vm.KbHomeViewModel
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

/**
 * KB tab default landing. Hosts KbHomeViewModel; renders search bar + counts row +
 * category chips + entries list. AND→OR rescue is transparent: SearchKbUseCase
 * (wired into the VM) folds the OR fallback into [state.entries] when AND is
 * sparse, so the screen never has to choose between two result lists.
 */
@Composable
fun KbHomeScreen(
    onOpenEntry: (Long) -> Unit,
) {
    val vm: KbHomeViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val displayedEntries = state.entries

    Box(Modifier.fillMaxSize().background(Void)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Amber)) { append("user@study") }
                        withStyle(SpanStyle(color = FoamDim)) { append(":~$ ") }
                        withStyle(SpanStyle(color = Foam)) { append("ls kb/") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "total ${state.notesCount + state.mistakesCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoamMute,
                )
            }
            Spacer(Modifier.height(10.dp))
            KbSearchBar(query = state.searchQuery, onQueryChange = vm::onSearchChange)

            // Top stats row: 3 mutually-exclusive filter chips. ALL is the default
            // so notes + mistakes show inline; tapping a selected chip clears back
            // to ALL (acts as a deselect).
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val total = state.notesCount + state.mistakesCount
                StatChip("全部", total, selected = state.filter == KbHomeFilter.ALL) {
                    vm.setFilter(KbHomeFilter.ALL)
                }
                Spacer(Modifier.width(12.dp))
                StatChip("笔记", state.notesCount, selected = state.filter == KbHomeFilter.NOTES_ONLY) {
                    vm.setFilter(if (state.filter == KbHomeFilter.NOTES_ONLY) KbHomeFilter.ALL else KbHomeFilter.NOTES_ONLY)
                }
                Spacer(Modifier.width(12.dp))
                StatChip("错题", state.mistakesCount, selected = state.filter == KbHomeFilter.MISTAKES_ONLY) {
                    vm.setFilter(if (state.filter == KbHomeFilter.MISTAKES_ONLY) KbHomeFilter.ALL else KbHomeFilter.MISTAKES_ONLY)
                }
            }

            CategoryChipRow(
                items = state.categories,
                selectedId = state.selectedCategoryId,
                onSelect = vm::onSelectCategory,
            )

            Text(
                "─────────── ${if (state.isSearching) "匹配" else "最近"} ───────────",
                color = FoamDim,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp),
            )

            if (displayedEntries.isEmpty()) {
                KbEmptyState()
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
private fun KbEmptyState() {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        Text("# 暂无条目", style = MaterialTheme.typography.bodyMedium, color = FoamMute)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Phosphor)) { append("▓ ") }
                    withStyle(SpanStyle(color = FoamDim)) { append("从 ") }
                    withStyle(SpanStyle(color = Cyan)) { append("[聊天]") }
                    withStyle(SpanStyle(color = FoamDim)) { append(" 或 ") }
                    withStyle(SpanStyle(color = Cyan)) { append("[扫描]") }
                    withStyle(SpanStyle(color = FoamDim)) { append(" 归档生成") }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            BlinkingCursor()
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
