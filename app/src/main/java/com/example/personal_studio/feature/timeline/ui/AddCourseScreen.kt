package com.example.personal_studio.feature.timeline.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.feature.timeline.ui.components.SemesterStartModal
import com.example.personal_studio.feature.timeline.ui.components.StepperRow
import com.example.personal_studio.feature.timeline.ui.components.WeekdayPicker
import com.example.personal_studio.feature.timeline.vm.AddCourseEvent
import com.example.personal_studio.feature.timeline.vm.AddCourseViewModel
import com.example.personal_studio.ui.components.KeyboardHandledByInsets
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgGroupLabel
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgTextField
import com.example.personal_studio.ui.shell.LocalShellToast
import com.example.personal_studio.ui.theme.Tg
import kotlinx.coroutines.flow.collectLatest

/**
 * 添加课程: name and details, then weekdays, periods and weeks; the line under the time card
 * says how many classes that makes. After saving the form clears for the next course.
 */
@Composable
fun AddCourseScreen(
    onBack: () -> Unit,
    onRequestNotifPermission: () -> Unit,
    vm: AddCourseViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = Tg.colors
    val toast = LocalShellToast.current
    val context = LocalContext.current
    KeyboardHandledByInsets()

    LaunchedEffect(Unit) {
        vm.events.collectLatest { ev ->
            if (ev is AddCourseEvent.RequestNotifPermission) onRequestNotifPermission()
        }
    }
    LaunchedEffect(ui.savedToast) {
        ui.savedToast?.let {
            if (toast != null) toast.show(it) else android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.consumedToast()
        }
    }
    if (ui.needsSemesterStart) {
        SemesterStartModal(onPicked = vm::onSemesterStartPicked, onDismiss = onBack)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TerminalTopBar(route = "添加课程", subtitle = "添加课程\n按星期、节次和周次生成每一次课", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
            TgGroupLabel("课程", Modifier.enterUp(true, 0))
            TgCard(Modifier.enterUp(true, 0)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TgTextField(ui.title, vm::onTitleChange, label = "课名", placeholder = "必填",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    TgTextField(ui.instructor, vm::onInstructorChange, label = "老师", placeholder = "可不填",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    TgTextField(ui.location, vm::onLocationChange, label = "地点", placeholder = "例如 理教 305",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    TgTextField(ui.credits, vm::onCreditsChange, label = "学分", placeholder = "可不填",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next))
                    TgTextField(ui.notes, vm::onNotesChange, label = "备注", singleLine = false, minLines = 2)
                }
            }

            TgGroupLabel("时间", Modifier.enterUp(true, 60))
            TgCard(Modifier.enterUp(true, 60)) {
                Column(Modifier.padding(16.dp)) {
                    Text("每周哪几天", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(bottom = 10.dp))
                    WeekdayPicker(ui.weekdays, vm::onToggleWeekday)
                    StepperRow("从第几节", ui.periodStart, 1, ui.maxPeriod, vm::onPeriodStart, Modifier.padding(top = 12.dp), unit = { "第 $it 节" })
                    StepperRow("到第几节", ui.periodEnd, ui.periodStart, ui.maxPeriod, vm::onPeriodEnd, unit = { "第 $it 节" })
                    StepperRow("从第几周", ui.weekStart, 1, ui.maxWeek, vm::onWeekStart, unit = { "第 $it 周" })
                    StepperRow("到第几周", ui.weekEnd, ui.weekStart, ui.maxWeek, vm::onWeekEnd, unit = { "第 $it 周" })
                }
            }
            val summary = courseDraftSummary(ui.weekdays, ui.periodStart, ui.periodEnd, ui.weekStart, ui.weekEnd)
            AnimatedContent(summary, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "draft") { line ->
                Text(
                    line ?: "选好星期后，这里会显示一共几次课。",
                    style = MaterialTheme.typography.bodySmall, color = if (line != null) c.text else c.textSecondary,
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp),
                )
            }
            ui.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = c.error, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
            }
        }
        TgPillButton(
            if (ui.saving) "正在保存…" else "保存",
            onClick = vm::save,
            enabled = ui.saveEnabled,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        )
    }
}
