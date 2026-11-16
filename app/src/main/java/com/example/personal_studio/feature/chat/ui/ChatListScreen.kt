package com.example.personal_studio.feature.chat.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.ChatSessionSummary
import com.example.personal_studio.feature.chat.vm.ChatListViewModel
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.feature.timeline.today.pressScale
import com.example.personal_studio.ui.components.SheetAction
import com.example.personal_studio.ui.components.TgBottomSheet
import com.example.personal_studio.ui.components.TgConfirmDialog
import com.example.personal_studio.ui.components.TgInputDialog
import com.example.personal_studio.ui.theme.Tg

/**
 * 问答: two ways in (type a question, or photograph one), then the conversations grouped by
 * 今天 / 近 7 天 / 更早. Long-press a conversation to rename or delete it.
 */
@Composable
fun ChatListScreen(
    onOpenSession: (Long) -> Unit,
    onPhotoQuestion: (Long) -> Unit = onOpenSession,
    vm: ChatListViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = Tg.colors
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        now = System.currentTimeMillis()
        onPauseOrDispose { }
    }
    var menuFor by remember { mutableStateOf<ChatSessionSummary?>(null) }
    var renameFor by remember { mutableStateOf<ChatSessionSummary?>(null) }
    var deleteFor by remember { mutableStateOf<ChatSessionSummary?>(null) }
    val groups = remember(state.sessions, now) { ChatListGrouping.group(state.sessions, now) { it.updatedAt } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp)) {
        item(key = "entries") {
            Row(Modifier.enterUp(true, 0), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EntryCard("新对话", "文字提问，支持公式", Icons.Outlined.ChatBubbleOutline, Modifier.weight(1f)) {
                    vm.createNewSession(onCreated = onOpenSession)
                }
                EntryCard("拍题提问", "拍下题目，直接讲解", Icons.Outlined.PhotoCamera, Modifier.weight(1f)) {
                    vm.createNewSession(onCreated = onPhotoQuestion)
                }
            }
        }
        if (state.sessions.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.padding(top = 40.dp, start = 4.dp, end = 4.dp).enterUp(true, 80)) {
                    Text("还没有对话", style = MaterialTheme.typography.headlineMedium, color = c.text)
                    Text(
                        "问课程里的问题、拍下题目让 AI 讲解；好的回答可以存进知识库，以后在资料里找到。",
                        style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        groups.forEach { g ->
            item(key = "grp-${g.label}") {
                Text(
                    g.label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                    color = c.textSecondary,
                    modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
                )
            }
            items(g.items, key = { it.id }) { s ->
                SessionRow(s, now, onClick = { onOpenSession(s.id) }, onLongClick = { menuFor = s }, modifier = Modifier.animateItem())
            }
        }
    }

    menuFor?.let { s ->
        TgBottomSheet(onDismiss = { menuFor = null }, header = displayTitle(s.title)) {
            SheetAction("重命名", icon = Icons.Outlined.DriveFileRenameOutline, onClick = { renameFor = s; menuFor = null })
            SheetAction("删除对话", icon = Icons.Outlined.DeleteOutline, destructive = true, onClick = { deleteFor = s; menuFor = null })
        }
    }
    renameFor?.let { s ->
        TgInputDialog(
            title = "重命名对话", initial = s.title,
            onConfirm = { vm.onRename(s.id, it); renameFor = null },
            onDismiss = { renameFor = null },
        )
    }
    deleteFor?.let { s ->
        TgConfirmDialog(
            title = "删除这段对话？", message = "“${displayTitle(s.title)}”及其中的 ${s.msgCount} 条消息会被删除，已存入知识库的条目不受影响。",
            confirmLabel = "删除",
            onConfirm = { vm.onDelete(s.id); deleteFor = null },
            onDismiss = { deleteFor = null },
        )
    }
}

@Composable
private fun EntryCard(title: String, hint: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = Tg.colors
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .heightIn(min = 112.dp)
            .pressScale(interaction)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(16.dp),
    ) {
        Box(Modifier.size(40.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.padding(top = 12.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    s: ChatSessionSummary,
    now: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tg.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "重命名或删除")
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(displayTitle(s.title), style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                s.lastSnippet?.let(ChatListGrouping::plainSnippet)?.takeIf { it.isNotBlank() } ?: "还没有消息",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(ChatListGrouping.rowTime(s.updatedAt, now), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            if (s.msgCount > 0) {
                Text("${s.msgCount} 条", style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                    color = c.textTertiary, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
