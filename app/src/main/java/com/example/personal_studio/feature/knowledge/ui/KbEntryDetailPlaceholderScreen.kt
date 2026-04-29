package com.example.personal_studio.feature.knowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.ui.components.MathMarkdownView
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Phase 3 placeholder for the KB entry detail. Shows enough of the saved
 * entry to confirm persistence worked. Phase 4 / Task 28 replaces this with
 * the full KbEntryDetailScreen.
 */
@Composable
fun KbEntryDetailPlaceholderScreen(
    entryId: Long,
    onBack: () -> Unit,
) {
    val vm: KbEntryDetailPlaceholderViewModel = hiltViewModel()
    val entry by vm.entry.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Void)) {
        Column(Modifier.fillMaxSize()) {
            TerminalTopBar(
                route = "kb/entry/$entryId",
                trailing = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back", tint = Foam)
                    }
                },
            )
            val e = entry
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                if (e == null) {
                    Text("$ loading entry #$entryId...", color = FoamDim, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("# ${e.title}", color = Phosphor, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "saved · ${e.categoryName ?: "其它"} · entry #${e.id}",
                        color = FoamDim,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(16.dp))
                    if (!e.standardizedQuestion.isNullOrBlank()) {
                        Text("## 题目", color = Phosphor, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        MathMarkdownView(markdown = e.standardizedQuestion!!, modifier = Modifier.fillMaxSize())
                        Spacer(Modifier.height(16.dp))
                    }
                    Text("## 摘要", color = Phosphor, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    MathMarkdownView(markdown = e.summaryMarkdown, modifier = Modifier.fillMaxSize())
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "▓ Phase 4 (Task 28) replaces this placeholder with the full detail screen.",
                        color = FoamDim,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@HiltViewModel
class KbEntryDetailPlaceholderViewModel @Inject constructor(
    repo: KnowledgeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val entryId: Long = savedStateHandle.get<Long>("entryId") ?: 0L
    val entry: StateFlow<KbEntry?> =
        repo.observeEntry(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
