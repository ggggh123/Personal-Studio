package com.example.personal_studio.feature.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Carmine
import java.time.Instant
import java.time.ZoneId

/**
 * Renders a horizontal red line + "$ now: HH:mm" label at the appropriate Y
 * inside a Box laid out at the same height as TimelineAxis.
 *
 * Caller passes the absolute epoch ms; this composable handles the conversion
 * to local minutes-into-day and translates that to a dp offset against the
 * 24h axis.
 */
@Composable
fun NowIndicator(
    nowEpoch: Long,
    displayDayStartEpoch: Long,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val nowLocal = Instant.ofEpochMilli(nowEpoch).atZone(zone).toLocalDateTime()
    val minutesIntoDay = nowLocal.toLocalTime().toSecondOfDay() / 60f
    val startMinutes = TimelineAxisSpec.START_HOUR * 60f
    val endMinutes = TimelineAxisSpec.END_HOUR * 60f
    if (minutesIntoDay < startMinutes || minutesIntoDay > endMinutes) return
    val topDp = (minutesIntoDay - startMinutes) / 60f * TimelineAxisSpec.PX_PER_HOUR_DP

    // Layout: Row keeps the label and 2.dp line horizontally separated via
    // Spacer + weight(1f), so they never overlap visually. The whole Row is
    // offset by (topDp - 8.dp): the row's intrinsic height equals the label's
    // font height (~16.dp for labelSmall), and verticalAlignment=CenterVertically
    // places both the label and the 2.dp line at the row's vertical centre.
    // Subtracting 8.dp (half of ~16.dp) makes the row's centre — and therefore
    // the 2.dp line — fall exactly on topDp. This matches the -8.dp trick the
    // hour labels in TimelineAxis already use.
    Box(modifier.fillMaxWidth().offset(y = (topDp - 8f).dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$ now: %02d:%02d".format(nowLocal.hour, nowLocal.minute),
                color = Carmine,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.width(4.dp))
            Box(Modifier.weight(1f).height(2.dp).background(Carmine))
        }
    }
}
