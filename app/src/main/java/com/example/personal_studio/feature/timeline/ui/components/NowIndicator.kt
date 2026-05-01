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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Carmine
import java.time.Instant
import java.time.ZoneId

/**
 * Renders a horizontal red line + dot + "$ now: HH:mm" label at the
 * appropriate Y inside a Box laid out at the same height as TimelineAxis.
 *
 * Caller passes the absolute epoch ms; this composable handles the conversion
 * to "minutes since 07:00 in display-day's timezone" and translates that to dp.
 */
@Composable
fun NowIndicator(
    nowEpoch: Long,
    displayDayStartEpoch: Long,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val nowLocal = Instant.ofEpochMilli(nowEpoch).atZone(zone).toLocalDateTime()
    val secondsIntoDay = nowLocal.toLocalTime().toSecondOfDay()
    val minutesIntoDay = secondsIntoDay / 60f
    val sevenAm = TimelineAxisSpec.START_HOUR * 60f
    val end = (TimelineAxisSpec.END_HOUR * 60f) + 30f
    if (minutesIntoDay < sevenAm || minutesIntoDay > end) return
    val topDp = (minutesIntoDay - sevenAm) / 60f * TimelineAxisSpec.PX_PER_HOUR_DP

    Box(modifier.fillMaxWidth().offset(y = topDp.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
