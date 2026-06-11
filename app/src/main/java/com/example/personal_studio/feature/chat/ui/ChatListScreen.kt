package com.example.personal_studio.feature.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.ChatSessionSummary
import com.example.personal_studio.feature.chat.vm.ChatListViewModel
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.components.TerminalBottomSheet
import com.example.personal_studio.ui.components.TerminalConfirmDialog
import com.example.personal_studio.ui.components.TerminalInputDialog
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(
    onOpenSession: (Long) -> Unit,
    vm: ChatListViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val now = remember(state.sessions) { System.currentTimeMillis() }

    var menuFor by remember { mutableStateOf<ChatSessionSummary?>(null) }
    var renameFor by remember { mutableStateOf<ChatSessionSummary?>(null) }
    var deleteFor by remember { mutableStateOf<ChatSessionSummary?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Amber)) { append("user@study") }
                    withStyle(SpanStyle(color = FoamDim)) { append(":~$ ") }
                    withStyle(SpanStyle(color = Foam)) { append("ls sessions/") }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "[+ 新建]", color = Cyan, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { vm.createNewSession(onCreated = onOpenSession) }.padding(4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("total ${state.sessions.size}", style = MaterialTheme.typography.bodySmall, color = FoamMute)
        Spacer(Modifier.height(14.dp))

        if (state.sessions.isEmpty()) {
            EmptyState(onOpenNew = { vm.createNewSession(onCreated = onOpenSession) })
        } else {
            val groups = remember(state.sessions, now) {
                ChatListGrouping.group(state.sessions, now) { it.updatedAt }
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                groups.forEach { g ->
                    item(key = "grp-${g.label}") { GroupHeader(g.label) }
                    items(g.items, key = { it.id }) { s ->
                        SessionRow(
                            s = s, now = now,
                            onClick = { onOpenSession(s.id) },
                            onLongClick = { menuFor = s },
                        )
                    }
                }
            }
        }
    }

    menuFor?.let { s ->
        TerminalBottomSheet(onDismiss = { menuFor = null }, header = "会话「${s.title}」") {
            ActionLine("▸ 重命名", Foam) { renameFor = s; menuFor = null }
            ActionLine("▸ 删除", Carmine) { deleteFor = s; menuFor = null }
        }
    }
    renameFor?.let { s ->
        TerminalInputDialog(
            title = "重命名会话", initial = s.title,
            onConfirm = { vm.onRename(s.id, it); renameFor = null },
            onDismiss = { renameFor = null },
        )
    }
    deleteFor?.let { s ->
        TerminalConfirmDialog(
            title = "删除会话", message = "删除会话「${s.title}」？此操作不可撤销。",
            confirmLabel = "删除",
            onConfirm = { vm.onDelete(s.id); deleteFor = null },
            onDismiss = { deleteFor = null },
        )
    }
}

@Composable private fun GroupHeader(label: String) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = FoamDim)) { append("── ") }
            withStyle(SpanStyle(color = FoamMute)) { append(label) }
            withStyle(SpanStyle(color = FoamDim)) { append(" ───────────────") }
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun SessionRow(
    s: ChatSessionSummary, now: Long, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("▸ ${s.title}", color = Foam, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(ChatListGrouping.rowTime(s.updatedAt, now), color = FoamMute,
                style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("最近: ${s.lastSnippet?.replace("\n", " ") ?: "—"}", color = FoamDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${s.msgCount} 条", color = FoamMute, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun ActionLine(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(label, color = color, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp))
}

@Composable private fun EmptyState(onOpenNew: () -> Unit) {
    Column {
        Text("# 暂无会话", style = MaterialTheme.typography.bodyMedium, color = FoamMute)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Phosphor)) { append("▓ ") }
                    withStyle(SpanStyle(color = FoamDim)) { append("点 ") }
                    withStyle(SpanStyle(color = Cyan)) { append("[新建]") }
                    withStyle(SpanStyle(color = FoamDim)) { append(" 开始第一个会话") }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onOpenNew),
            )
            BlinkingCursor()
        }
    }
}
