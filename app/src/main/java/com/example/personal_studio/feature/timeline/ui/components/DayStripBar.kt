package com.example.personal_studio.feature.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import java.time.LocalDate

@Composable
fun DayStripBar(
    weekStart: LocalDate,
    selectedDay: LocalDate,
    dayCounts: Map<LocalDate, Int>,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (i in 0..6) {
            val day = weekStart.plusDays(i.toLong())
            val isSelected = day == selectedDay
            DayDot(
                day = day,
                count = dayCounts[day] ?: 0,
                isSelected = isSelected,
                onClick = { onSelectDay(day) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayDot(
    day: LocalDate,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelColor = if (isSelected) Phosphor else FoamDim
    val dots = when {
        count == 0 -> ""
        count <= 2 -> "·"
        count <= 4 -> "··"
        else -> "···"
    }
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .background(if (isSelected) Rule else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(4.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "%02d-%02d".format(day.monthValue, day.dayOfMonth),
            color = labelColor, style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(2.dp))
        Text(text = dots, color = if (isSelected) Foam else Phosphor,
            style = MaterialTheme.typography.bodySmall)
    }
}
