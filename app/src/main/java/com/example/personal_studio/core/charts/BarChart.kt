package com.example.personal_studio.core.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.core.util.GradeBucket
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor

/** 水平条形分布。F 段用 Carmine(红)，其余 Phosphor(绿)。 */
@Composable
fun GradeBarChart(buckets: List<GradeBucket>, modifier: Modifier = Modifier) {
    val maxCount = (buckets.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        buckets.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.label, color = FoamMute, modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.labelMedium)
                Box(
                    Modifier
                        .fillMaxWidth(fraction = b.count.toFloat() / maxCount)
                        .height(14.dp)
                        .background(if (b.label == "F") Carmine else Phosphor),
                )
                Spacer(Modifier.width(8.dp))
                Text("${b.count}", color = FoamMute, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
