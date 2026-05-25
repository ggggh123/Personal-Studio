package com.example.personal_studio.core.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.core.util.GradeBucket
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor

@Composable
fun GradeBarChart(buckets: List<GradeBucket>, modifier: Modifier = Modifier) {
    val maxCount = (buckets.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    val total = buckets.sumOf { it.count }.coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        buckets.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.label, color = FoamMute, modifier = Modifier.width(20.dp),
                    style = MaterialTheme.typography.labelMedium)
                Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Box(
                        Modifier.fillMaxWidth(fraction = b.count.toFloat() / maxCount)
                            .widthIn(min = 3.dp).height(16.dp)
                            .background(if (b.label == "F") Carmine else Phosphor, RoundedCornerShape(2.dp)),
                    )
                }
                Text(
                    "${b.count} · ${b.count * 100 / total}%",
                    color = Foam, modifier = Modifier.width(72.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
