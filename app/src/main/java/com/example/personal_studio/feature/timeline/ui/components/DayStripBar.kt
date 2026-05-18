package com.example.personal_studio.feature.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personal_studio.domain.model.DayActivityCount
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import java.time.LocalDate

@Composable
fun DayStripBar(
    weekStart: LocalDate,
    selectedDay: LocalDate,
    dayCounts: Map<LocalDate, DayActivityCount>,
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
            DayCell(
                day = day,
                counts = dayCounts[day] ?: DayActivityCount(0, 0),
                isSelected = isSelected,
                onClick = { onSelectDay(day) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    counts: DayActivityCount,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelColor = if (isSelected) Phosphor else FoamDim
    Column(
        modifier
            .clickable(onClick = onClick)
            .background(if (isSelected) Rule else Color.Transparent, RoundedCornerShape(4.dp))
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "%02d-%02d".format(day.monthValue, day.dayOfMonth),
            color = if (isSelected) Foam else labelColor,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(4.dp))
        // Two type-coloured count badges; either one is hidden when its count is 0.
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (counts.courses > 0) CountBadge(counts.courses, Cyan)
            if (counts.tasks > 0) CountBadge(counts.tasks, Amber)
        }
    }
}

@Composable
private fun CountBadge(count: Int, color: Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .border(1.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // includeFontPadding=false drops the legacy ~4dp font padding so the
        // glyph sits visually centred inside the circle. lineHeight pinned to
        // fontSize keeps the vertical box tight.
        Text(
            text = count.toString(),
            style = TextStyle(
                color = color,
                fontSize = 9.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Medium,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}
