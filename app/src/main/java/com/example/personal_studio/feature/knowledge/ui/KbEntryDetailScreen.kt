package com.example.personal_studio.feature.knowledge.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.personal_studio.feature.knowledge.vm.KbSourceLink
import com.example.personal_studio.feature.knowledge.vm.KbSourceTarget
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.SheetAction
import com.example.personal_studio.ui.components.TgBottomSheet
import com.example.personal_studio.ui.components.TgConfirmDialog
import com.example.personal_studio.ui.components.TgInputDialog
import com.example.personal_studio.ui.components.TgTopBar
import com.example.personal_studio.ui.components.TgBusyOverlay
import com.example.personal_studio.ui.components.TgEmptyState
import com.example.personal_studio.ui.shell.LocalShellToast
import com.example.personal_studio.ui.theme.Tg
import kotlinx.coroutines.launch
import java.io.File

/**
 * A knowledge entry: where it came from (tap to open the conversation or scanned page),
 * the photo and question for a mistake, the summary, and related entries. The ⋯ menu
 * renames, moves to another category, regenerates the summary or deletes.
 */
@Composable
fun KbEntryDetailScreen(
    onBack: () -> Unit,
    onOpenSource: (KbSourceTarget) -> Unit,
    onOpenRelated: (Long) -> Unit,
) {
    val vm: KbEntryDetailViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val source by vm.source.collectAsStateWithLifecycle()
    val categories by vm.observeCategoriesForUi.collectAsStateWithLifecycle(initialValue = emptyList())
    val errorMessage by vm.errorMessage.collectAsStateWithLifecycle()
    val isBusy by vm.isBusy.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("正在保存") }
    val scope = rememberCoroutineScope()
    val toast = LocalShellToast.current
    val context = LocalContext.current
    val entry = (state as? KbEntryDetailUiState.Loaded)?.entry

    // Failed operations (a regenerate timing out, a save error) surface as a short note.
    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            if (toast != null) toast.show(msg)
            else android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            vm.clearError()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TgTopBar(
                route = "知识条目",
                subtitle = entry?.let { it.title + "\n" + KbText.detailCaption(it) },
                onBack = onBack,
                trailing = if (entry != null) {
                    {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreHoriz, contentDescription = "更多操作", tint = Tg.colors.text)
                        }
                    }
                } else null,
            )
            when (val s = state) {
                is KbEntryDetailUiState.Loading -> Unit
                is KbEntryDetailUiState.NotFound -> TgEmptyState(
                    "条目已不存在", "它可能已经在别处被删除了。",
                    Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    actionLabel = "返回", onAction = onBack,
                )
                is KbEntryDetailUiState.Loaded -> Loaded(
                    s, source, onOpenSource, onOpenRelated,
                    onSaveSummary = { busyLabel = "正在保存"; vm.saveSummary(it) },
                    onSaveQuestion = { busyLabel = "正在保存"; vm.saveStandardizedQuestion(it) },
                )
            }
        }

        if (showMenu && entry != null) {
            TgBottomSheet(onDismiss = { showMenu = false }, header = entry.title) {
                SheetAction("重命名", icon = Icons.Outlined.DriveFileRenameOutline, onClick = { showMenu = false; showRename = true })
                SheetAction("更改分类", icon = Icons.Outlined.FolderOpen, hint = entry.categoryName ?: "未分类",
                    onClick = { showMenu = false; showCategorySheet = true })
                SheetAction("重新生成摘要", icon = Icons.Outlined.Refresh, hint = "按原始内容再整理一次",
                    onClick = { showMenu = false; showRegenerateConfirm = true })
                SheetAction("删除条目", icon = Icons.Outlined.DeleteOutline, destructive = true,
                    onClick = { showMenu = false; showDeleteConfirm = true })
            }
        }
        if (showRename && entry != null) {
            TgInputDialog(
                title = "重命名条目",
                initial = entry.title,
                onConfirm = { busyLabel = "正在保存"; vm.rename(it); showRename = false },
                onDismiss = { showRename = false },
            )
        }
        if (showCategorySheet && entry != null) {
            CategoryPickerSheet(
                categories = categories,
                selectedId = entry.categoryId,
                onPick = { busyLabel = "正在保存"; vm.changeCategory(it.id) },
                onCreate = { name ->
                    scope.launch {
                        val newId = vm.upsertCategoryAndUse(name)
                        vm.changeCategory(newId)
                    }
                },
                onDismiss = { showCategorySheet = false },
            )
        }
        if (showDeleteConfirm && entry != null) {
            TgConfirmDialog(
                title = "删除这个条目？",
                message = "“${entry.title}”和随它保存的图片会一并删除，无法恢复。原对话和扫描文档不受影响。",
                confirmLabel = "删除",
                onConfirm = { busyLabel = "正在删除"; vm.delete(onBack); showDeleteConfirm = false },
                onDismiss = { showDeleteConfirm = false },
            )
        }
        if (showRegenerateConfirm) {
            TgConfirmDialog(
                title = "重新生成摘要？",
                message = "会再调用一次模型，用新的结果覆盖摘要" + (if (entry?.isMistake == true) "和题目" else "") + "；标题和分类不变。",
                confirmLabel = "重新生成",
                onConfirm = { busyLabel = "正在重新生成摘要"; vm.regenerate(); showRegenerateConfirm = false },
                onDismiss = { showRegenerateConfirm = false },
            )
        }

        if (isBusy) TgBusyOverlay(busyLabel)
    }
}

@Composable
private fun Loaded(
    state: KbEntryDetailUiState.Loaded,
    source: KbSourceLink?,
    onOpenSource: (KbSourceTarget) -> Unit,
    onOpenRelated: (Long) -> Unit,
    onSaveSummary: (String) -> Unit,
    onSaveQuestion: (String) -> Unit,
) {
    val e = state.entry
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (source != null) {
            item(key = "source") { SourceLink(e, source, onOpenSource, Modifier.enterUp(true, 0)) }
        }
        if (e.isMistake) {
            item(key = "question") {
                Column(Modifier.enterUp(true, 60)) {
                    e.originalImagePath?.let { path ->
                        val shape = RoundedCornerShape(16.dp)
                        AsyncImage(
                            model = File(path),
                            contentDescription = "题目原图",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .clip(shape)
                                .border(1.dp, Tg.colors.line, shape),
                        )
                    }
                    SummaryMarkdownEditor(initial = e.standardizedQuestion.orEmpty(), label = "题目", onSave = onSaveQuestion)
                }
            }
        }
        item(key = "summary") {
            SummaryMarkdownEditor(
                initial = e.summaryMarkdown,
                label = if (e.isMistake) "讲解" else "摘要",
                onSave = onSaveSummary,
                modifier = Modifier.enterUp(true, 100),
            )
        }
        item(key = "related") { RelatedEntriesSection(related = state.related, onOpen = onOpenRelated) }
        item(key = "bottom") { Box(Modifier.navigationBarsPadding()) }
    }
}

/** "来自 问答 · 数列极限" with a chevron; a plain line once the source has been deleted. */
@Composable
private fun SourceLink(e: KbEntry, link: KbSourceLink, onOpen: (KbSourceTarget) -> Unit, modifier: Modifier) {
    val c = Tg.colors
    val target = link.target
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .border(1.dp, c.line, shape)
            .then(if (target != null) Modifier.clickable(role = Role.Button, onClickLabel = "打开来源") { onOpen(target) } else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (e.sourceScanPageId != null) Icons.Outlined.Description else Icons.Outlined.ChatBubbleOutline,
            contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text("来源", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Text(link.label, style = MaterialTheme.typography.bodyMedium, color = if (target != null) c.text else c.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (target != null) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}
