package com.example.personal_studio.feature.timeline.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.personal_studio.core.util.CourseColorPalette
import com.example.personal_studio.domain.model.BubbleState
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Olive
import com.example.personal_studio.ui.theme.Phosphor
import java.time.Instant
import java.time.ZoneId

@Composable
fun TimelineBubble(
    item: TimelineItem,
    state: BubbleState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val baseColor = bubbleBaseColor(item, state)
    val (borderWidth, alpha, scale) = visualMods(state)

    val infinite = rememberInfiniteTransition(label = "bubble-pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "scale",
    )
    val finalScale = if (scale) pulse else 1f

    val timeRange = remember(item) {
        val start = Instant.ofEpochMilli(item.startAt).atZone(zone).toLocalDateTime().toLocalTime()
        val end = item.endAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().toLocalTime() }
        if (end == null) "%02d:%02d".format(start.hour, start.minute)
        else "%02d:%02d - %02d:%02d".format(start.hour, start.minute, end.hour, end.minute)
    }

    Column(
        modifier
            .fillMaxWidth()
            .scale(finalScale)
            .alpha(alpha)
            .clickable(onClick = onClick)
            .background(baseColor.copy(alpha = 0.20f), RoundedCornerShape(6.dp))
            .border(borderWidth.dp, baseColor, RoundedCornerShape(6.dp))
            .padding(8.dp),
    ) {
        Text(
            text = item.title,
            color = Foam,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (state is BubbleState.TaskDone || state is BubbleState.CustomDone)
                TextDecoration.LineThrough else TextDecoration.None,
        )
        Spacer(Modifier.height(2.dp))
        Text(text = "$timeRange${item.location?.let { "  ·  $it" } ?: ""}",
            color = FoamDim, style = MaterialTheme.typography.labelSmall)
    }
}

private fun bubbleBaseColor(item: TimelineItem, state: BubbleState): Color = when (state) {
    BubbleState.CourseUpcoming, BubbleState.CourseImminent,
    BubbleState.CourseInProgress, BubbleState.CoursePast -> CourseColorPalette.colorFor(item.title)

    BubbleState.TaskUpcoming, BubbleState.TaskImminent -> Amber
    BubbleState.TaskOverdue -> Carmine
    BubbleState.TaskDone -> Olive

    BubbleState.CustomUpcoming, BubbleState.CustomImminent,
    BubbleState.CustomInProgress -> Phosphor
    BubbleState.CustomOverdue -> Carmine
    BubbleState.CustomDone -> Olive
}

private data class VisualMods(val borderWidth: Int, val alpha: Float, val scale: Boolean)

private fun visualMods(state: BubbleState): VisualMods = when (state) {
    BubbleState.CourseUpcoming, BubbleState.TaskUpcoming, BubbleState.CustomUpcoming -> VisualMods(1, 1f, false)
    BubbleState.CourseImminent, BubbleState.TaskImminent, BubbleState.CustomImminent -> VisualMods(2, 1f, true)
    BubbleState.CourseInProgress, BubbleState.CustomInProgress -> VisualMods(2, 1f, false)
    BubbleState.CoursePast -> VisualMods(1, 0.5f, false)
    BubbleState.TaskOverdue, BubbleState.CustomOverdue -> VisualMods(2, 1f, true)
    BubbleState.TaskDone, BubbleState.CustomDone -> VisualMods(1, 0.8f, false)
}

@Suppress("unused") private fun guard(@Suppress("UNUSED_PARAMETER") t: TimelineType) {
    // type referenced only to keep import warnings down
}
