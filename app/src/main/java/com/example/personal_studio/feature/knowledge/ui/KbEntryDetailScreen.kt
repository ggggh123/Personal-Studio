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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.example.personal_studio.feature.knowledge.ui.components.CategoryPickerSheet
import com.example.personal_studio.feature.knowledge.ui.components.RelatedEntriesSection
import com.example.personal_studio.feature.knowledge.ui.components.SummaryMarkdownEditor
import com.example.personal_studio.feature.knowledge.vm.KbEntryDetailUiState
import com.example.personal_studio.feature.knowledge.vm.KbEntryDetailViewModel
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.components.TerminalConfirmDialog
import com.example.personal_studio.ui.components.TerminalDropdownItem
import com.example.personal_studio.ui.components.TerminalDropdownMenu
import com.example.personal_studio.ui.components.TerminalInputDialog
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
                            TerminalDropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                TerminalDropdownItem("重命名") { showRename = true; menuExpanded = false }
                                TerminalDropdownItem("改分类") { showCategorySheet = true; menuExpanded = false }
                                TerminalDropdownItem("重新生成") { showRegenerateConfirm = true; menuExpanded = false }
                                TerminalDropdownItem("删除", Carmine) { showDeleteConfirm = true; menuExpanded = false }
                            }
                        }
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back", tint = Foam)
                        }
                    }
                },
            )
            when (val s = state) {
                is KbEntryDetailUiState.Loading -> Centered { Text("$ 加载中…", color = FoamDim) }
                is KbEntryDetailUiState.NotFound -> Centered {
                    Text("! 条目未找到", color = Carmine)
                }
                is KbEntryDetailUiState.Loaded -> Loaded(s, vm, onOpenSource, onOpenRelated)
            }
        }

        if (showRename) {
            TerminalInputDialog(
                title = "重命名条目",
                initial = (state as? KbEntryDetailUiState.Loaded)?.entry?.title.orEmpty(),
                onConfirm = { vm.rename(it); showRename = false },
                onDismiss = { showRename = false },
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
            TerminalConfirmDialog(
                title = "删除此条目",
                message = "将删除 kb_entries 行 + 本地图片 + 关联关系，无法撤销。",
                confirmLabel = "删除",
                onConfirm = { vm.delete(onBack); showDeleteConfirm = false },
                onDismiss = { showDeleteConfirm = false },
            )
        }
        if (showRegenerateConfirm) {
            TerminalConfirmDialog(
                title = "重新生成摘要",
                message = "将重新调 LLM 覆盖 摘要 + 标准化题目；标题/分类保留。",
                confirmLabel = "重新生成",
                onConfirm = { vm.regenerate(); showRegenerateConfirm = false },
                onDismiss = { showRegenerateConfirm = false },
            )
        }

        // Busy overlay: any in-flight op (especially regenerate, which may take
        // 30-60s) parks the screen behind a dim scrim with a phosphor spinner so
        // the user knows something is happening + can't fire concurrent ops.
        if (isBusy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                    .clickable(enabled = false, onClick = {}),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$ 处理中…", color = FoamDim, style = MaterialTheme.typography.bodyMedium)
                    BlinkingCursor()
                }
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
                label = "摘要",
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
