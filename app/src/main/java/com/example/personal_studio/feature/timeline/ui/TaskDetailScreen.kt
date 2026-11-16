package com.example.personal_studio.feature.timeline.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.feature.timeline.today.DetailAction
import com.example.personal_studio.feature.timeline.today.ItemDetailContent
import com.example.personal_studio.feature.timeline.vm.TaskDetailEvent
import com.example.personal_studio.feature.timeline.vm.TaskDetailViewModel
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.components.TerminalConfirmDialog
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Tg
import kotlinx.coroutines.flow.collectLatest
import java.time.ZoneId

/**
 * Full-page item detail, reached from a reminder notification (deep link). Same content as
 * the sheet on 今日.
 */
@Composable
fun TaskDetailScreen(
    itemId: Long,
    onBack: () -> Unit,
    onOpenCourseSeries: (Long) -> Unit,
    vm: TaskDetailViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = Tg.colors
    LaunchedEffect(itemId) { vm.load(itemId) }
    LaunchedEffect(Unit) {
        vm.events.collectLatest { ev -> if (ev is TaskDetailEvent.Closed) onBack() }
    }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(route = "detail", subtitle = "事项详情", onBack = onBack)
        if (ui.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BlinkingCursor() }
            return@Column
        }
        val item = ui.item ?: run {
            Text(ui.error ?: "这一项已删除", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                modifier = Modifier.padding(20.dp))
            return@Column
        }
        val actions = buildList {
            when (item.type) {
                TimelineType.COURSE -> {
                    add(DetailAction("删除本次", primary = false) { confirmDelete = true })
                    item.seriesId?.let { sid -> add(DetailAction("管理整个系列", primary = true) { onOpenCourseSeries(sid) }) }
                }
                TimelineType.EXAM -> add(DetailAction(if (item.isDone) "取消已考" else "标记已考", primary = true, onClick = vm::onToggleDone))
                TimelineType.TASK, TimelineType.CUSTOM -> {
                    add(DetailAction("删除", primary = false) { confirmDelete = true })
                    add(DetailAction(if (item.isDone) "标记未完成" else "标记完成", primary = true, onClick = vm::onToggleDone))
                }
            }
        }
        ItemDetailContent(
            item = item,
            now = System.currentTimeMillis(),
            zone = remember { ZoneId.systemDefault() },
            actions = actions,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 28.dp),
        )
        if (confirmDelete) {
            TerminalConfirmDialog(
                title = if (item.type == TimelineType.COURSE) "删除这次课？" else "删除这一项？",
                message = "“${item.title}”会从日程中移除，提醒也会一并取消。",
                confirmLabel = "删除",
                onConfirm = {
                    confirmDelete = false
                    vm.onDelete()
                },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}
