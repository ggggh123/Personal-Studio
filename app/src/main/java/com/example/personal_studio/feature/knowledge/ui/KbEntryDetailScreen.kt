package com.example.personal_studio.feature.knowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.feature.knowledge.ui.components.RelatedEntriesSection
import com.example.personal_studio.feature.knowledge.vm.KbEntryDetailUiState
import com.example.personal_studio.feature.knowledge.vm.KbEntryDetailViewModel
import com.example.personal_studio.ui.components.MathMarkdownView
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import java.io.File

/**
 * KB entry detail screen — Phase 4 baseline (normal-entry variant only).
 * The mistake variant (image + standardized-question header) lands in Task 33.
 * Edit ops (rename / change category / delete / regenerate / edit summary) land
 * in Task 40+.
 */
@Composable
fun KbEntryDetailScreen(
    onBack: () -> Unit,
    onOpenSource: (KbEntry) -> Unit,
    onOpenRelated: (Long) -> Unit,
) {
    val vm: KbEntryDetailViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Void)) {
        Column(Modifier.fillMaxSize()) {
            TerminalTopBar(
                route = (state as? KbEntryDetailUiState.Loaded)?.entry?.title.orEmpty().ifBlank { "kb/" },
                trailing = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back", tint = Foam)
                    }
                },
            )
            when (val s = state) {
                is KbEntryDetailUiState.Loading -> Centered { CircularProgressIndicator(color = Phosphor) }
                is KbEntryDetailUiState.NotFound -> Centered {
                    Text("! entry not found", color = Carmine)
                }
                is KbEntryDetailUiState.Loaded -> Loaded(s, onOpenSource, onOpenRelated)
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun Loaded(
    state: KbEntryDetailUiState.Loaded,
    onOpenSource: (KbEntry) -> Unit,
    onOpenRelated: (Long) -> Unit,
) {
    val e = state.entry
    LazyColumn(Modifier.fillMaxSize()) {
        item { MetadataRow(e, onOpenSource) }
        if (e.isMistake) {
            item { MistakeHeader(e) }
        }
        item {
            MathMarkdownView(
                markdown = e.summaryMarkdown,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
        item { RelatedEntriesSection(related = state.related, onOpen = onOpenRelated) }
        item { Spacer(Modifier.height(48.dp)) }
    }
}

@Composable
private fun MetadataRow(e: KbEntry, onOpenSource: (KbEntry) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Phosphor)) { append("▎") }
                withStyle(SpanStyle(color = Foam)) { append(" ${e.categoryName ?: "其它"} · ") }
                withStyle(SpanStyle(color = if (e.isMistake) Cyan else FoamDim)) {
                    append(if (e.isMistake) "错题" else e.source.name)
                }
                withStyle(SpanStyle(color = FoamDim)) { append(" · 来自: ") }
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            "[↗]",
            color = Phosphor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .clickable { onOpenSource(e) }
                .padding(8.dp),
        )
    }
}

@Composable
private fun MistakeHeader(e: KbEntry) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        e.originalImagePath?.let { path ->
            AsyncImage(
                model = File(path),
                contentDescription = "原图",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }
        Text("## 题目", color = Phosphor, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        MathMarkdownView(
            markdown = e.standardizedQuestion.orEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}
