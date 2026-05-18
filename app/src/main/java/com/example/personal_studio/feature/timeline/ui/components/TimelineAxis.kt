package com.example.personal_studio.feature.timeline.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Rule

object TimelineAxisSpec {
    const val START_HOUR = 0
    const val END_HOUR = 24            // exclusive top bound — 24:00 == midnight next day
    const val PX_PER_HOUR_DP = 64
    val totalHeightDp = (END_HOUR - START_HOUR) * PX_PER_HOUR_DP // 24 * 64 = 1536
}

@Composable
fun TimelineAxis(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(TimelineAxisSpec.totalHeightDp.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pxPerHour = size.height / (TimelineAxisSpec.END_HOUR - TimelineAxisSpec.START_HOUR).toFloat()
            // Draw an hour line every hour from 00:00 to 24:00 (inclusive top + bottom edge).
            for (h in TimelineAxisSpec.START_HOUR..TimelineAxisSpec.END_HOUR) {
                val y = (h - TimelineAxisSpec.START_HOUR) * pxPerHour
                drawLine(
                    color = Rule,
                    start = Offset(48.dp.toPx(), y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        // Hourly labels — skip the very last "24:00" since it duplicates the next day's 00:00.
        for (h in TimelineAxisSpec.START_HOUR until TimelineAxisSpec.END_HOUR) {
            val topDp = (h - TimelineAxisSpec.START_HOUR) * TimelineAxisSpec.PX_PER_HOUR_DP
            Text(
                text = "%02d:00".format(h),
                color = FoamMute,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 4.dp).offset(y = topDp.dp - 8.dp),
            )
        }
    }
}
