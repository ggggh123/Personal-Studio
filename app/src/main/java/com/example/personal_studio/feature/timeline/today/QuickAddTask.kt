package com.example.personal_studio.feature.timeline.today

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.domain.timeline.AddTaskUseCase
import com.example.personal_studio.domain.timeline.ScheduleRemindersUseCase
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.TgMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/** A preset deadline of the quick task sheet. */
data class QuickDue(val label: String, val epoch: Long)

/** 今晚 23:59 · 明天 12:00 · 本周日 (on Sunday: 下周日) 23:59. */
fun quickDues(now: Long, zone: ZoneId): List<QuickDue> {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    fun at(date: java.time.LocalDate, time: LocalTime) = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    val late = LocalTime.of(23, 59)
    val sunday = if (today.dayOfWeek == DayOfWeek.SUNDAY) today.plusDays(7) else today.plusDays(7L - today.dayOfWeek.value)
    return listOf(
        QuickDue("今晚 23:59", at(today, late)),
        QuickDue("明天 12:00", at(today.plusDays(1), LocalTime.NOON)),
        QuickDue((if (today.dayOfWeek == DayOfWeek.SUNDAY) "下周日" else "本周日") + " 23:59", at(sunday, late)),
    )
}

/** Late in the evening the default moves from tonight to tomorrow noon. */
fun defaultDueIndex(now: Long, zone: ZoneId): Int =
    if (Instant.ofEpochMilli(now).atZone(zone).hour >= 21) 1 else 0

data class QuickAddUiState(
    val title: String = "",
    val dues: List<QuickDue> = emptyList(),
    val dueIndex: Int = 0,
    val showError: Boolean = false,
    val saving: Boolean = false,
)

@HiltViewModel
class QuickAddTaskViewModel @Inject constructor(
    private val addTask: AddTaskUseCase,
    private val schedule: ScheduleRemindersUseCase,
    private val repo: TimelineRepository,
    private val focus: TodayFocus,
    private val clock: TodayClock,
    private val zone: ZoneId,
) : ViewModel() {

    private val _ui = MutableStateFlow(QuickAddUiState())
    val ui: StateFlow<QuickAddUiState> = _ui.asStateFlow()

    /** Fresh form with deadlines computed from now. */
    fun reset() {
        val now = clock.now()
        _ui.value = QuickAddUiState(dues = quickDues(now, zone), dueIndex = defaultDueIndex(now, zone))
    }

    fun onTitle(text: String) = _ui.update { it.copy(title = text, showError = false) }

    fun onDue(index: Int) = _ui.update { it.copy(dueIndex = index) }

    /** Saves and schedules reminders; [onSaved] runs on success (the sheet closes, 今日 focuses it). */
    fun save(onSaved: () -> Unit) {
        val s = _ui.value
        if (s.saving) return
        if (s.title.isBlank()) {
            _ui.update { it.copy(showError = true) }
            return
        }
        val due = s.dues.getOrNull(s.dueIndex) ?: return
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching {
                addTask(type = TimelineType.TASK, title = s.title, description = null, startAt = due.epoch, endAt = null, location = null)
            }.onSuccess { id ->
                repo.findById(id)?.let { schedule(it) }
                focus.focus(id)
                _ui.update { it.copy(saving = false) }
                onSaved()
            }.onFailure {
                _ui.update { it.copy(saving = false) }
            }
        }
    }
}

/**
 * ＋ → 新建待办: title and one of three preset deadlines, saved in one tap. "其他时间…"
 * opens the full form for any date or an event with a time span.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddTaskSheet(
    visible: Boolean,
    state: QuickAddUiState,
    onTitle: (String) -> Unit,
    onDue: (Int) -> Unit,
    onSave: () -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Tg.colors
    BackHandler(enabled = visible, onBack = onDismiss)
    com.example.personal_studio.ui.components.KeyboardHandledByInsets(visible)
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(300)), exit = fadeOut(tween(250))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "取消新建待办", onClick = onDismiss),
        )
    }
    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(TgMotion.standard<IntOffset>()) { it },
            exit = slideOutVertically(TgMotion.exit<IntOffset>()) { it },
        ) {
            val focus = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(Unit) {
                delay(260)
                runCatching { focus.requestFocus() }
                keyboard?.show()
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(c.surfaceRaised)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .semantics { paneTitle = "新建待办" }
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp)
                        .background(c.line, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(14.dp))
                Text("新建待办", style = MaterialTheme.typography.headlineMedium.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize * 0.92f), color = c.text)
                Label("要做什么", Modifier.padding(top = 16.dp))
                val border by animateColorAsState(if (state.showError) c.error else c.line, tween(200), label = "titleBorder")
                BasicTextField(
                    value = state.title,
                    onValueChange = onTitle,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text, fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.94f),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(48.dp)
                        .focusRequester(focus)
                        .background(c.bg, RoundedCornerShape(14.dp))
                        .border(1.dp, border, RoundedCornerShape(14.dp)),
                    decorationBox = { inner ->
                        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                            if (state.title.isEmpty()) {
                                Text("交电路实验报告", style = MaterialTheme.typography.bodyLarge, color = c.textTertiary)
                            }
                            inner()
                        }
                    },
                )
                AnimatedVisibility(visible = state.showError) {
                    Text("先写下要做的事", style = MaterialTheme.typography.bodySmall, color = c.error, modifier = Modifier.padding(top = 6.dp))
                }
                Label("截止时间", Modifier.padding(top = 16.dp))
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.dues.forEachIndexed { i, due -> DueChip(due.label, i == state.dueIndex) { onDue(i) } }
                    DueChip("其他时间…", selected = false, onClick = onMore)
                }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                    Text("截止前 1 天、2 小时、30 分钟各提醒一次，到期时提示逾期。", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(DetailAction("取消", primary = false, onClick = onDismiss), Modifier.weight(1f))
                    PillButton(DetailAction(if (state.saving) "正在添加…" else "添加", primary = true, onClick = onSave), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
        color = Tg.colors.textSecondary,
        modifier = modifier,
    )
}

@Composable
private fun DueChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Tg.colors
    val shape = RoundedCornerShape(20.dp)
    val bg by animateColorAsState(if (selected) c.text else Color.Transparent, tween(200), label = "dueBg")
    val fg by animateColorAsState(if (selected) c.bg else c.text, tween(200), label = "dueFg")
    val bd by animateColorAsState(if (selected) c.text else c.textTertiary, tween(200), label = "dueBd")
    Box(
        Modifier
            .height(40.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, bd, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize), color = fg)
    }
}
