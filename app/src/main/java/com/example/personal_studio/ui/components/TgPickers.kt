package com.example.personal_studio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Tg
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Calendar dialog in the 天光 palette; [hint] is a line under the title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TgDatePickerDialog(
    title: String,
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    hint: String? = null,
) {
    val c = Tg.colors
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TgPillButton(
                "确定",
                onClick = { state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
                enabled = state.selectedDateMillis != null,
                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
            )
        },
        dismissButton = {
            TgPillButton("取消", onClick = onDismiss, style = PillStyle.SECONDARY, modifier = Modifier.padding(end = 8.dp, bottom = 8.dp))
        },
        colors = DatePickerDefaults.colors(containerColor = c.surfaceRaised),
    ) {
        DatePicker(
            state = state,
            title = {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = c.text,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 20.dp))
            },
            headline = {
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 8.dp))
                }
            },
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                containerColor = c.surfaceRaised,
                selectedDayContainerColor = c.accent,
                selectedDayContentColor = c.onAccent,
                todayDateBorderColor = c.accent,
                todayContentColor = c.accent,
                selectedYearContainerColor = c.accent,
                selectedYearContentColor = c.onAccent,
            ),
        )
    }
}

/** 24-hour clock dialog in the 天光 palette. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TgTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onPick: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Tg.colors
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    TerminalDialog(onDismiss = onDismiss, title = title) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = c.bg,
                    selectorColor = c.accent,
                    clockDialSelectedContentColor = c.onAccent,
                    clockDialUnselectedContentColor = c.text,
                    timeSelectorSelectedContainerColor = c.accentSoft,
                    timeSelectorSelectedContentColor = c.accent,
                    timeSelectorUnselectedContainerColor = c.bg,
                    timeSelectorUnselectedContentColor = c.text,
                ),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TgPillButton("取消", onClick = onDismiss, style = PillStyle.SECONDARY, modifier = Modifier.weight(1f))
            TgPillButton("确定", onClick = { onPick(state.hour, state.minute) }, modifier = Modifier.weight(1f))
        }
        Box(Modifier.height(2.dp))
    }
}
