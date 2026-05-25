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
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import java.util.Locale

@Composable
fun GpaOverviewCard(book: GradeBook, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat("总 GPA", String.format(Locale.US, "%.2f", book.overallGpa), Phosphor)
        Stat("加权均分", book.overallAvgScore?.let { String.format(Locale.US, "%.1f", it) } ?: "—", Cyan)
        Stat("总学分", String.format(Locale.US, "%.1f", book.totalCredits), Phosphor)
    }
}

@Composable
private fun Stat(label: String, value: String, valueColor: Color) {
    Column {
        Text(value, color = valueColor, style = MaterialTheme.typography.titleLarge)
        Text(label, color = FoamMute, style = MaterialTheme.typography.labelMedium)
    }
}
