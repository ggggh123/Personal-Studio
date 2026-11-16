package com.example.personal_studio.feature.scanner.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.ScanDocumentSummary
import com.example.personal_studio.domain.model.SortMode
import com.example.personal_studio.feature.chat.ui.ChatListGrouping
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.SheetAction
import com.example.personal_studio.ui.components.TerminalBottomSheet
import com.example.personal_studio.ui.components.TerminalConfirmDialog
import com.example.personal_studio.ui.components.TerminalInputDialog
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgEmptyState
import com.example.personal_studio.ui.theme.Tg

/** Sort orders offered on the scans page, with their labels. */
internal val ScanSorts = listOf(
    SortMode.TIME_DESC to "按创建时间",
    SortMode.RECENT_UPDATED to "按最近修改",
    SortMode.ALPHA_ASC to "按名称",
)

/** "3 页 · 昨天": the date follows the sort (last change when sorted by it, otherwise creation). */
internal fun scanDocMeta(doc: ScanDocumentSummary, sort: SortMode, now: Long): String =
    "${doc.pageCount} 页 · " + ChatListGrouping.rowTime(if (sort == SortMode.RECENT_UPDATED) doc.updatedAt else doc.createdAt, now)

/**
 * 资料 → 扫描文档: a new-scan card, then the documents as paper covers in two columns.
 * Long-press a cover to rename or delete it.
 */
@Composable
fun ScanLibraryScreen(
    onOpenDoc: (docId: Long) -> Unit,
    onNewDoc: () -> Unit,
) {
    val vm: ScanLibraryViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = Tg.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        now = System.currentTimeMillis()
        onPauseOrDispose { }
    }

    var actionTarget by remember { mutableStateOf<ScanDocumentSummary?>(null) }
    var renameTarget by remember { mutableStateOf<ScanDocumentSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ScanDocumentSummary?>(null) }
    var showSort by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "new", span = { GridItemSpan(maxLineSpan) }) {
            NewScanCard(onNewDoc, Modifier.enterUp(true, 0))
        }
        if (state.docs.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                TgEmptyState(
                    "还没有扫描文档",
                    "拍下讲义、作业或试卷，自动裁边和增强，多页合成一份文档，可以导出 PDF 或把某一页存进知识库。",
                    Modifier.padding(top = 12.dp).enterUp(true, 80),
                )
            }
        } else {
            item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().enterUp(true, 60), verticalAlignment = Alignment.CenterVertically) {
                    Text("${state.docs.size} 份文档", style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                        color = c.textSecondary, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    Row(
                        Modifier
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button, onClickLabel = "更改排序") { showSort = true }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.SwapVert, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                        Text(ScanSorts.first { it.first == state.sort }.second, style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                            modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            items(state.docs, key = { it.id }) { doc ->
                ScanDocCover(
                    doc = doc,
                    meta = scanDocMeta(doc, state.sort, now),
                    onClick = { onOpenDoc(doc.id) },
                    onLongClick = { actionTarget = doc },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    if (showSort) {
        TerminalBottomSheet(onDismiss = { showSort = false }, header = "排序") {
            ScanSorts.forEach { (mode, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.RadioButton) { vm.setSort(mode); showSort = false }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val sel = mode == state.sort
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = if (sel) c.accent else c.text, modifier = Modifier.weight(1f))
                    if (sel) Icon(Icons.Rounded.Check, contentDescription = "当前", tint = c.accent, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    actionTarget?.let { doc ->
        TerminalBottomSheet(onDismiss = { actionTarget = null }, header = doc.title) {
            SheetAction("重命名", icon = Icons.Outlined.DriveFileRenameOutline, onClick = { actionTarget = null; renameTarget = doc })
            SheetAction("删除文档", icon = Icons.Outlined.DeleteOutline, destructive = true, onClick = { actionTarget = null; deleteTarget = doc })
        }
    }
    renameTarget?.let { doc ->
        TerminalInputDialog(
            title = "重命名文档",
            initial = doc.title,
            onConfirm = { newTitle -> renameTarget = null; vm.onRename(doc.id, newTitle) },
            onDismiss = { renameTarget = null },
        )
    }
    deleteTarget?.let { doc ->
        TerminalConfirmDialog(
            title = "删除这份文档？",
            message = "“${doc.title}”的 ${doc.pageCount} 页会一并删除，无法恢复。已存入知识库的条目不受影响。",
            confirmLabel = "删除",
            onConfirm = { deleteTarget = null; vm.onDelete(doc.id) },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun NewScanCard(onClick: () -> Unit, modifier: Modifier) {
    val c = Tg.colors
    TgCard(modifier, onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.DocumentScanner, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("新建扫描", style = MaterialTheme.typography.titleMedium, color = c.text)
                Text("自动识边、增强，多页合成 PDF", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
