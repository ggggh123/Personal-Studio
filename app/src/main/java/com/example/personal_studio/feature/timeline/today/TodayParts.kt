package com.example.personal_studio.feature.timeline.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.model.DayActivityCount
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.TgMotion
import java.time.LocalDate

/** Sinks slightly while pressed, springing back on release. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.975f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, TgMotion.fast(), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Remembers where a tappable element sits on screen so a sheet can grow out of it
 * (container transform). Read [Holder.bounds] at tap time.
 */
class BoundsHolder {
    var coordinates: LayoutCoordinates? = null
    val bounds: androidx.compose.ui.geometry.Rect?
        get() = coordinates?.takeIf { it.isAttached }?.let {
            val pos = it.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
            androidx.compose.ui.geometry.Rect(pos, androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()))
        }
}

fun Modifier.trackBounds(holder: BoundsHolder): Modifier = onGloballyPositioned { holder.coordinates = it }

/** The card under the header: what is on now, or next, or tomorrow's first item. */
@Composable
fun NextItemCard(
    text: NextCardText,
    onOpen: (androidx.compose.ui.geometry.Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tg.colors
    val interaction = remember { MutableInteractionSource() }
    val bounds = remember { BoundsHolder() }
    val shape = RoundedCornerShape(20.dp)
    val progress by animateFloatAsState(text.progress ?: 0f, tween(1000), label = "nextProgress")
    Column(
        modifier
            .fillMaxWidth()
            .trackBounds(bounds)
            .pressScale(interaction)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .then(
                if (text.itemId != null) {
                    Modifier.clickable(interaction, indication = null, role = Role.Button, onClickLabel = "查看详情") {
                        onOpen(bounds.bounds)
                    }
                } else Modifier,
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(6.dp).background(c.accent, CircleShape))
            Text(text.label, style = MaterialTheme.typography.bodySmall, color = c.accent)
        }
        Text(
            text.title,
            style = MaterialTheme.typography.headlineMedium,
            color = c.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (text.meta.isNotEmpty()) {
            Text(text.meta, style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        if (text.progress != null) {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.line),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(c.accent, RoundedCornerShape(2.dp)))
            }
        }
    }
}

/**
 * Monday to Sunday of the selected week. The selected day is a pill in the text colour;
 * today is marked in the accent; dots show courses (left) and open tasks or exams (right).
 */
@Composable
fun DayStrip(
    weekStart: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    counts: Map<LocalDate, DayActivityCount>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tg.colors
    Row(modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0 until 7) {
            val day = weekStart.plusDays(i.toLong())
            val isSel = day == selected
            val isToday = day == today
            val count = counts[day]
            val bg by animateColorAsState(if (isSel) c.text else Color.Transparent, tween(300), label = "stripBg")
            val fg by animateColorAsState(
                when { isSel -> c.bg; isToday -> c.accent; else -> c.text }, tween(300), label = "stripFg",
            )
            val sub by animateColorAsState(if (isSel) c.bg else c.textSecondary, tween(300), label = "stripSub")
            val description = (if (isToday) "今天，" else "") + "${day.monthValue} 月 ${day.dayOfMonth} 日 ${TodayText.weekday(day)}"
            Column(
                Modifier
                    .width(46.dp)
                    .height(62.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .clickable(role = Role.Tab) { onSelect(day) }
                    .semantics {
                        contentDescription = description
                        this.selected = isSel
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(TodayText.weekdayShort(day), style = MaterialTheme.typography.bodySmall, color = sub)
                Text(
                    day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = fg,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(4.dp).background(if ((count?.courses ?: 0) > 0) sub else Color.Transparent, CircleShape))
                    Box(Modifier.size(4.dp).background(if ((count?.tasks ?: 0) > 0) (if (isSel) c.bg else c.accent) else Color.Transparent, CircleShape))
                }
            }
        }
    }
}
