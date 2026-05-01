package com.example.personal_studio.feature.timeline.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.feature.timeline.vm.TaskDetailEvent
import com.example.personal_studio.feature.timeline.vm.TaskDetailViewModel
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.ZoneId

@Composable
fun TaskDetailScreen(
    itemId: Long,
    onBack: () -> Unit,
    onOpenCourseSeries: (Long) -> Unit,
    vm: TaskDetailViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(itemId) { vm.load(itemId) }
    LaunchedEffect(Unit) {
        vm.events.collectLatest { ev -> if (ev is TaskDetailEvent.Closed) onBack() }
    }

    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(route = "detail", subtitle = "# timeline item", trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
        })

        if (ui.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        val item = ui.item ?: run {
            Text(ui.error ?: "条目已删除", color = Foam, modifier = Modifier.padding(20.dp))
            return@Column
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.type.name, color = Phosphor, style = MaterialTheme.typography.labelMedium)
            Text(item.title, color = Foam, style = MaterialTheme.typography.titleLarge)
            val zone = ZoneId.systemDefault()
            val s = Instant.ofEpochMilli(item.startAt).atZone(zone).toLocalDateTime()
            val timeStr = if (item.endAt != null) {
                val e = Instant.ofEpochMilli(item.endAt).atZone(zone).toLocalDateTime()
                "$s — $e"
            } else "$s"
            Text(timeStr, color = FoamDim)
            item.location?.let { Text("地点: $it", color = FoamDim) }
            item.instructor?.let { Text("老师: $it", color = FoamDim) }
            item.description?.let { Text(it, color = Foam) }
            item.notes?.let { Text("备注: $it", color = FoamDim) }

            Spacer(Modifier.height(8.dp))
            when (item.type) {
                TimelineType.COURSE -> {
                    Text("改名 / 改老师 / 改整学期 → Settings → 课程列表",
                        color = FoamDim, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::onDelete) { Text("删除本次") }
                        OutlinedButton(onClick = { item.seriesId?.let(onOpenCourseSeries) }) { Text("管理整个系列") }
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::onToggleDone) {
                            Text(if (item.isDone) "↻ 取消完成" else "✓ 完成")
                        }
                        OutlinedButton(onClick = vm::onDelete) { Text("删除") }
                    }
                }
            }
        }
    }
}
