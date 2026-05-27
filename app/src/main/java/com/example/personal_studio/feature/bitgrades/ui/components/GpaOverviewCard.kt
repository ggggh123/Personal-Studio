package com.example.personal_studio.feature.bitgrades.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import java.util.Locale

/**
 * 概览卡。调用方决定显示总体值还是选中子集的值（同一布局，复用样式）。
 * filtering=true 时,标签前缀"选中"以标明上下文。
 */
@Composable
fun GpaOverviewCard(
    gpa: Double,
    avgScore: Double?,
    credits: Double,
    filtering: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat(if (filtering) "选中 GPA" else "总 GPA",
            String.format(Locale.US, "%.2f", gpa), Phosphor)
        Stat(if (filtering) "选中均分" else "加权均分",
            avgScore?.let { String.format(Locale.US, "%.1f", it) } ?: "—", Cyan)
        Stat(if (filtering) "选中学分" else "总学分",
            String.format(Locale.US, "%.1f", credits), Phosphor)
    }
}

@Composable
private fun Stat(label: String, value: String, valueColor: Color) {
    Column {
        Text(value, color = valueColor, style = MaterialTheme.typography.titleLarge)
        Text(label, color = FoamMute, style = MaterialTheme.typography.labelMedium)
    }
}
