package com.example.personal_studio.feature.bitgrades.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import java.util.Locale

@Composable
fun GpaOverviewCard(book: GradeBook, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat("总 GPA", String.format(Locale.US, "%.2f", book.overallGpa))
        Stat("总学分", String.format(Locale.US, "%.1f", book.totalCredits))
        Stat("专业排名", book.overallRank?.majorPercentile?.let { "前 $it%" } ?: "—")
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, color = Phosphor, style = MaterialTheme.typography.titleLarge)
        Text(label, color = FoamMute, style = MaterialTheme.typography.labelMedium)
    }
}
