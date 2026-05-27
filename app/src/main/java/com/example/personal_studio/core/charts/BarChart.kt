package com.example.personal_studio.core.charts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.core.util.GradeBucket
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor

private val ALL_BANDS = listOf("A", "B", "C", "D", "F")

@Composable
fun GradeBarChart(buckets: List<GradeBucket>, modifier: Modifier = Modifier) {
    val byLabel = buckets.associateBy { it.label }
    val maxCount = (buckets.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    val total = buckets.sumOf { it.count }.coerceAtLeast(1)
    // 数字桶时按 A-F 固定行渲染（缺的桶 count=0、AnimatedVisibility 折叠）；这样
    // 选中变化时行不会瞬增瞬减，柱宽 + 显隐都是渐变。非数字桶(优秀/良好…)按原样列。
    val isLetterBands = buckets.isNotEmpty() && buckets.all { it.label in ALL_BANDS }
    val labels = if (isLetterBands) ALL_BANDS else buckets.map { it.label }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { label ->
            val b = byLabel[label]
            val visible = b != null && b.count > 0
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
            ) {
                val count = b?.count ?: 0
                val targetFraction = count.toFloat() / maxCount
                val animFraction by animateFloatAsState(
                    targetValue = targetFraction.coerceIn(0f, 1f),
                    animationSpec = tween(durationMillis = 400),
                    label = "bar-$label",
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = FoamMute, modifier = Modifier.width(20.dp),
                        style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Box(
                            Modifier.fillMaxWidth(fraction = animFraction.coerceAtLeast(0.001f))
                                .widthIn(min = 3.dp).height(16.dp)
                                .background(if (label == "F") Carmine else Phosphor, RoundedCornerShape(2.dp)),
                        )
                    }
                    Text(
                        "$count · ${count * 100 / total}%",
                        color = Foam, modifier = Modifier.width(72.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
