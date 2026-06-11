package com.example.personal_studio.feature.scanner.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.ScanDocumentSummary
import com.example.personal_studio.domain.model.SortMode
import com.example.personal_studio.ui.components.ScanThumbnail
import com.example.personal_studio.ui.components.TerminalBottomSheet
import com.example.personal_studio.ui.components.TerminalConfirmDialog
import com.example.personal_studio.ui.components.TerminalInputDialog
import com.example.personal_studio.ui.placeholder.ScannerPlaceholder
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * /scans tab。可排序的扫描文档列表 + `[+ 新建扫描]`;空时回退 ScannerPlaceholder。
 * 长按行弹终端动作菜单:未完成件 恢复/丢弃;完成件 重命名/删除。行内渲染首页封面缩略图。
 */
@Composable
fun ScanLibraryScreen(
    onOpenDoc: (docId: Long) -> Unit,
    onResumeDoc: (docId: Long) -> Unit,
    onNewDoc: () -> Unit,
) {
    val vm: ScanLibraryViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    var actionTarget by remember { mutableStateOf<ScanDocumentSummary?>(null) }
    var renameTarget by remember { mutableStateOf<ScanDocumentSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ScanDocumentSummary?>(null) }

    Column(Modifier.fillMaxSize().background(Void)) {
        // 壳层(MainShell)已为 tab 路由渲染 studio:~/scanner $ 顶栏 + 设置齿轮;此屏**不再**
        // 自带 TerminalTopBar(否则与壳层顶栏重叠成两行)。新建动作放进正文右上,排序状态由
        // 下方 SortToolbar 自身呈现。
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                "[+ 新建扫描]",
                color = Phosphor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onNewDoc),
            )
        }
        SortToolbar(current = state.sort, onSelect = vm::setSort)
        if (state.docs.isEmpty()) {
            ScannerPlaceholder()
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 20.dp, vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.docs, key = { it.id }) { doc ->
                    DocRow(
                        doc,
                        onClick = { onOpenDoc(doc.id) },
                        onLongPress = { actionTarget = doc },
                    )
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Rule),
                    )
                }
            }
        }
    }

    actionTarget?.let { doc ->
        DocActionsDialog(
            doc = doc,
            onDismiss = { actionTarget = null },
            onResume = { actionTarget = null; onResumeDoc(doc.id) },
            onDiscard = { actionTarget = null; vm.onDelete(doc.id) },
            onRename = { actionTarget = null; renameTarget = doc },
            onDelete = { actionTarget = null; deleteTarget = doc },
        )
    }
    renameTarget?.let { doc ->
        TerminalInputDialog(
            title = "重命名扫描",
            initial = doc.title,
            onConfirm = { newTitle -> renameTarget = null; vm.onRename(doc.id, newTitle) },
            onDismiss = { renameTarget = null },
        )
    }
    deleteTarget?.let { doc ->
        TerminalConfirmDialog(
            title = "删除扫描",
            message = "删除「${doc.title}」？此操作不可撤销。",
            confirmLabel = "删除",
            onConfirm = { deleteTarget = null; vm.onDelete(doc.id) },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun SortToolbar(current: SortMode, onSelect: (SortMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(
            SortMode.TIME_DESC to "时间",
            SortMode.ALPHA_ASC to "名称",
            SortMode.RECENT_UPDATED to "最近",
        ).forEach { (mode, label) ->
            val active = mode == current
            Text(
                text = if (active) "[$label]" else label,
                style = MaterialTheme.typography.bodySmall,
                color = if (active) Phosphor else FoamDim,
                modifier = Modifier.clickable { onSelect(mode) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocRow(
    doc: ScanDocumentSummary,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScanThumbnail(path = doc.coverPath)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = FoamDim)) { append("drwx── ") }
                    if (doc.isPending) {
                        withStyle(SpanStyle(color = Amber)) { append("[未完成] ") }
                    }
                    withStyle(SpanStyle(color = Foam)) { append(doc.title) }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${doc.pageCount} 页 · ${formatTs(doc.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = FoamDim,
            )
        }
    }
}

@Composable
private fun DocActionsDialog(
    doc: ScanDocumentSummary,
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val header = if (doc.isPending) "「${doc.title}」· 未完成" else "「${doc.title}」"
    TerminalBottomSheet(onDismiss = onDismiss, header = header) {
        if (doc.isPending) {
            ActionLine("▸ 恢复", Phosphor, onResume)
            ActionLine("▸ 丢弃", Amber, onDiscard)
        } else {
            ActionLine("▸ 重命名", Foam, onRename)
            ActionLine("▸ 删除", Carmine, onDelete)
        }
    }
}

@Composable
private fun ActionLine(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}

private fun formatTs(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(ts))
