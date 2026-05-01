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
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Rule

object TimelineAxisSpec {
    const val START_HOUR = 7
    const val END_HOUR = 23
    const val END_HALF = true // ends at 23:30
    const val PX_PER_HOUR_DP = 64
    val totalHeightDp = ((END_HOUR - START_HOUR) + 0.5f) * PX_PER_HOUR_DP // 16.5 * 64
}

@Composable
fun TimelineAxis(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(TimelineAxisSpec.totalHeightDp.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pxPerHour = size.height / ((TimelineAxisSpec.END_HOUR - TimelineAxisSpec.START_HOUR) + 0.5f)
            for (h in TimelineAxisSpec.START_HOUR..TimelineAxisSpec.END_HOUR) {
                val y = (h - TimelineAxisSpec.START_HOUR) * pxPerHour
                drawLine(
                    color = Rule,
                    start = Offset(48.dp.toPx(), y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }
        }
        for (h in TimelineAxisSpec.START_HOUR..TimelineAxisSpec.END_HOUR) {
            val topDp = (h - TimelineAxisSpec.START_HOUR) * TimelineAxisSpec.PX_PER_HOUR_DP
            Text(
                text = "%02d:00".format(h),
                color = FoamDim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp).offset(y = topDp.dp - 8.dp),
            )
        }
    }
}
