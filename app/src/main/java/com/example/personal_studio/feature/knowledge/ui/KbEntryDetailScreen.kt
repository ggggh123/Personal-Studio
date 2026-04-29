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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.feature.knowledge.ui.components.CategoryPickerSheet
import com.example.personal_studio.feature.knowledge.ui.components.RelatedEntriesSection
import com.example.personal_studio.feature.knowledge.ui.components.SummaryMarkdownEditor
import com.example.personal_studio.feature.knowledge.vm.KbEntryDetailUiState
import com.example.personal_studio.feature.knowledge.vm.KbEntryDetailViewModel
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import kotlinx.coroutines.launch
import java.io.File

/**
 * KB entry detail screen — Phase 6 edit-suite variant.
 * TopBar carries an overflow menu wiring rename / change category / regenerate
 * / delete; the summary section (and the standardized-question header in the
 * mistake variant) renders via [SummaryMarkdownEditor] for inline preview/edit.
 */
@Composable
fun KbEntryDetailScreen(
    onBack: () -> Unit,
    onOpenSource: (KbEntry) -> Unit,
    onOpenRelated: (Long) -> Unit,
) {
    val vm: KbEntryDetailViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val categories by vm.observeCategoriesForUi.collectAsStateWithLifecycle(initialValue = emptyList())
    val errorMessage by vm.errorMessage.collectAsStateWithLifecycle()
    val isBusy by vm.isBusy.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Surface op failures (regenerate timeout, save errors) as a toast. Without
    // this any thrown exception in the VM op coroutines would have crashed the
    // process via viewModelScope's default exception handler.
    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            vm.clearError()
        }
    }

    Box(Modifier.fillMaxSize().background(Void)) {
        Column(Modifier.fillMaxSize()) {
            TerminalTopBar(
                route = (state as? KbEntryDetailUiState.Loaded)?.entry?.title.orEmpty().ifBlank { "kb/" },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "menu", tint = Foam)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("rename") },
                                    onClick = {
                                        renameDraft = (state as? KbEntryDetailUiState.Loaded)?.entry?.title.orEmpty()
                                        showRename = true
                                        menuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("change category") },
                                    onClick = { showCategorySheet = true; menuExpanded = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("regenerate") },
                                    onClick = { showRegenerateConfirm = true; menuExpanded = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("delete", color = Carmine) },
                                    onClick = { showDeleteConfirm = true; menuExpanded = false },
                                )
                            }
                        }
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back", tint = Foam)
                        }
                    }
                },
            )
            when (val s = state) {
                is KbEntryDetailUiState.Loading -> Centered { CircularProgressIndicator(color = Phosphor) }
                is KbEntryDetailUiState.NotFound -> Centered {
                    Text("! entry not found", color = Carmine)
                }
                is KbEntryDetailUiState.Loaded -> Loaded(s, vm, onOpenSource, onOpenRelated)
            }
        }

        if (showRename) {
            AlertDialog(
                onDismissRequest = { showRename = false },
                title = { Text("rename entry") },
                text = {
                    BasicTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                        cursorBrush = SolidColor(Phosphor),
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { vm.rename(renameDraft); showRename = false }) {
                        Text("save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRename = false }) { Text("cancel") }
                },
            )
        }
        if (showCategorySheet) {
            CategoryPickerSheet(
                categories = categories,
                selectedId = (state as? KbEntryDetailUiState.Loaded)?.entry?.categoryId,
                onPick = { vm.changeCategory(it.id) },
                onCreate = { name ->
                    coroutineScope.launch {
                        val newId = vm.upsertCategoryAndUse(name)
                        vm.changeCategory(newId)
                    }
                },
                onDismiss = { showCategorySheet = false },
            )
        }
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("delete this entry?") },
                text = { Text("kb_entries 行 + 本地 image + 关联关系会一并删除，无法撤销。") },
                confirmButton = {
                    TextButton(onClick = { vm.delete(onBack); showDeleteConfirm = false }) {
                        Text("delete", color = Carmine)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("cancel") }
                },
            )
        }
        if (showRegenerateConfirm) {
            AlertDialog(
                onDismissRequest = { showRegenerateConfirm = false },
                title = { Text("regenerate summary?") },
                text = { Text("将重新调 LLM 覆盖 summaryMarkdown + standardizedQuestion；标题 / 分类保留。") },
                confirmButton = {
                    TextButton(onClick = { vm.regenerate(); showRegenerateConfirm = false }) {
                        Text("regenerate")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRegenerateConfirm = false }) { Text("cancel") }
                },
            )
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
    vm: KbEntryDetailViewModel,
    onOpenSource: (KbEntry) -> Unit,
    onOpenRelated: (Long) -> Unit,
) {
    val e = state.entry
    LazyColumn(Modifier.fillMaxSize()) {
        item { MetadataRow(e, onOpenSource) }
        if (e.isMistake) {
            item { MistakeHeader(e, vm) }
        }
        item {
            SummaryMarkdownEditor(
                initial = e.summaryMarkdown,
                label = "summary",
                onSave = { md -> vm.saveSummary(md) },
                modifier = Modifier.fillMaxWidth(),
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
private fun MistakeHeader(e: KbEntry, vm: KbEntryDetailViewModel) {
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
        SummaryMarkdownEditor(
            initial = e.standardizedQuestion.orEmpty(),
            label = "题目",
            onSave = { md -> vm.saveStandardizedQuestion(md) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}
