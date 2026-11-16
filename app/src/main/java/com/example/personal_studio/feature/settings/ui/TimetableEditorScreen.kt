package com.example.personal_studio.feature.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.settings.vm.TimetableEditorViewModel
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.TerminalConfirmDialog
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgTimePickerDialog
import com.example.personal_studio.ui.shell.LocalShellToast
import com.example.personal_studio.ui.theme.Tg
import kotlinx.coroutines.delay

/**
 * 作息表: one row per period with its start and end; tapping a time opens a clock picker.
 * Saving re-times every future course and re-schedules their reminders.
 */
@Composable
fun TimetableEditorScreen(
    onBack: () -> Unit,
    vm: TimetableEditorViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = Tg.colors
    val toast = LocalShellToast.current
    var editing by remember { mutableStateOf<Pair<Int, Boolean>?>(null) } // (period index, isStart)

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TerminalTopBar(route = "作息表", subtitle = "作息表\n${ui.periods.size} 节 · 点时间修改", onBack = onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "note") {
                Text(
                    "已按北京理工大学的作息设置。改动会调整所有未来课程的上下课时间，一般不需要修改。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(c.accentSoft, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                )
            }
            items(ui.periods, key = { it.index }) { p ->
                Row(
                    Modifier.fillMaxWidth().animateItem(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("第 ${p.index} 节", style = MaterialTheme.typography.bodyLarge, color = c.text,
                        modifier = Modifier.padding(start = 4.dp).weight(1f))
                    TimeBox(p.startHHmm, "第 ${p.index} 节上课") { editing = p.index to true }
                    Text("–", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, modifier = Modifier.padding(horizontal = 8.dp))
                    TimeBox(p.endHHmm, "第 ${p.index} 节下课") { editing = p.index to false }
                }
            }
            item(key = "rows") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TgPillButton("加一节", onClick = vm::onAddRow, style = PillStyle.SECONDARY, modifier = Modifier.weight(1f))
                    TgPillButton("删最后一节", onClick = vm::onRemoveLast, style = PillStyle.SECONDARY, modifier = Modifier.weight(1f))
                    TgPillButton("恢复默认", onClick = vm::onResetDefault, style = PillStyle.SECONDARY, modifier = Modifier.weight(1f))
                }
            }
        }
        AnimatedVisibility(ui.error != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Text(
                ui.error.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = c.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        ui.error?.let { msg ->
            LaunchedEffect(msg) {
                delay(4_000)
                vm.consumedError()
            }
        }
        TgPillButton(
            if (ui.saving) "正在保存…" else "保存",
            onClick = vm::openConfirmDialog,
            enabled = !ui.saving,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        )
    }

    editing?.let { (index, isStart) ->
        val period = ui.periods.firstOrNull { it.index == index }
        if (period != null) {
            val (h, m) = (if (isStart) period.startHHmm else period.endHHmm).split(":")
                .let { (it.getOrNull(0)?.toIntOrNull() ?: 8) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
            TgTimePickerDialog(
                title = "第 $index 节${if (isStart) "上课" else "下课"}",
                initialHour = h, initialMinute = m,
                onPick = { hour, minute ->
                    val t = "%02d:%02d".format(hour, minute)
                    vm.onPeriodChange(index, startHHmm = if (isStart) t else null, endHHmm = if (isStart) null else t)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
    }
    if (ui.confirmDialogVisible) {
        TerminalConfirmDialog(
            title = "更新作息表？",
            message = "所有未来课程会按新的时间调整，提醒也会重新安排。",
            confirmLabel = "更新",
            onConfirm = {
                vm.save(onComplete = { touched ->
                    toast?.show(if (touched.isEmpty()) "作息表已保存" else "作息表已更新，${touched.size} 次课已调整时间")
                })
            },
            onDismiss = vm::closeConfirmDialog,
        )
    }
}

@Composable
private fun TimeBox(text: String, label: String, onClick: () -> Unit) {
    val c = Tg.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .width(76.dp)
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .clickable(role = Role.Button, onClickLabel = "修改$label") { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = c.text)
    }
}
