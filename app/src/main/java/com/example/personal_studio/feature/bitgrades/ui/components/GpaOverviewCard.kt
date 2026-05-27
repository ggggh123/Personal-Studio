package com.example.personal_studio.feature.bitgrades.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personal_studio.core.util.CreditFormat
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
    peerGpa: Double? = null,
    peerAvgScore: Double? = null,
    filtering: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // ~400ms tween on the three values—numbers smoothly count to the new target
    // when selection changes (instead of snapping).
    val spec = tween<Float>(durationMillis = 400)
    val animGpa by animateFloatAsState(gpa.toFloat(), animationSpec = spec, label = "gpa")
    val animAvg by animateFloatAsState((avgScore ?: 0.0).toFloat(), animationSpec = spec, label = "avg")
    val animCredits by animateFloatAsState(credits.toFloat(), animationSpec = spec, label = "credits")
    val animPeerGpa by animateFloatAsState((peerGpa ?: 0.0).toFloat(), animationSpec = spec, label = "peerGpa")
    val animPeerAvg by animateFloatAsState((peerAvgScore ?: 0.0).toFloat(), animationSpec = spec, label = "peerAvg")
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(if (filtering) "选中 GPA" else "总 GPA",
                String.format(Locale.US, "%.2f", animGpa), Phosphor)
            Stat(if (filtering) "选中均分" else "加权均分",
                if (avgScore == null) "—" else String.format(Locale.US, "%.1f", animAvg), Cyan)
            Stat(if (filtering) "选中学分" else "总学分",
                CreditFormat.format(animCredits.toDouble()), Phosphor)
        }
        if (peerGpa != null || peerAvgScore != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append(if (filtering) "选中估计平均绩  " else "估计平均绩  ")
                    peerGpa?.let { append("GPA ").append(String.format(Locale.US, "%.2f", animPeerGpa)) }
                    if (peerGpa != null && peerAvgScore != null) append("  ·  ")
                    peerAvgScore?.let { append("均分 ").append(String.format(Locale.US, "%.1f", animPeerAvg)) }
                },
                color = FoamMute,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, valueColor: Color) {
    Column {
        Text(value, color = valueColor, style = MaterialTheme.typography.titleLarge)
        Text(label, color = FoamMute, style = MaterialTheme.typography.labelMedium)
    }
}
