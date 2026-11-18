package com.example.personal_studio.feature.timeline.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.TgMotion
import com.example.personal_studio.ui.theme.daylight.MoonPhase
import com.example.personal_studio.ui.theme.daylight.SunDay
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * Entrance of the 今日 page: fades in and rises 12 dp after [delayMs]. With [play] false
 * (the page was already shown in this session) it starts settled.
 */
@Composable
fun Modifier.enterUp(play: Boolean, delayMs: Int): Modifier {
    val progress = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) progress.animateTo(1f, tween(560, delayMillis = delayMs, easing = FastOutSlowInEasing))
    }
    val rise = with(LocalDensity.current) { 12.dp.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}

/**
 * Header of 今日: account and sync pill, weekday and teaching week, the date in serif, a
 * tagline, and the horizon. Tapping the horizon reveals sunrise and sunset; long-press
 * drag previews the day ([onPreview]).
 */
@Composable
fun TodayHeader(
    date: LocalDate,
    week: Int?,
    hour: Double,
    sun: SunDay,
    tagline: String,
    syncLabel: String?,
    play: Boolean,
    onOpenSettings: () -> Unit,
    onOpenSync: () -> Unit,
    onPreview: (Double?) -> Unit,
) {
    val c = Tg.colors
    var infoShown by remember { mutableStateOf(false) }
    var scrubHour by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(infoShown) {
        if (infoShown) {
            delay(3_600)
            infoShown = false
        }
    }
    val shownHour = scrubHour ?: hour
    val period = periodOf(((shownHour % 24) + 24) % 24, sun)
    val isDay = hour >= sun.sunrise && hour <= sun.sunset
    val toRise = (((sun.sunrise - hour) % 24) + 24) % 24
    val phase = MoonPhase.fraction(java.time.Instant.now())
    val info = if (isDay) {
        "日出 ${TodayText.hhmm(sun.sunrise)} · 日落 ${TodayText.hhmm(sun.sunset)} · 距日落 ${TodayText.hours(sun.sunset - hour)}"
    } else {
        "${MoonPhase.name(phase)} · 日出 ${TodayText.hhmm(sun.sunrise)} · 还有 ${TodayText.hours(toRise)}"
    }
    val description = periodOf(hour, sun).label + "，" +
        (if (isDay) "距日落 ${TodayText.hours(sun.sunset - hour)}" else "距日出 ${TodayText.hours(toRise)}") +
        "。轻点查看日出日落，长按后左右拖动可预览一天"

    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "设置", onClick = onOpenSettings)
                    .semantics { contentDescription = "设置" },
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.size(36.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            if (syncLabel != null) {
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button, onClickLabel = "一键同步", onClick = onOpenSync),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier
                            .height(32.dp)
                            .border(1.dp, c.line, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
                        Text(syncLabel, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    TodayText.weekday(date) + (week?.let { " · 第 $it 周" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                    color = c.textSecondary,
                    modifier = Modifier.enterUp(play, 0),
                )
                Text(
                    "${date.monthValue} 月 ${date.dayOfMonth} 日",
                    style = MaterialTheme.typography.displayMedium,
                    color = c.text,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp).enterUp(play, 50),
                )
                Box(Modifier.heightIn(min = 24.dp).enterUp(play, 120)) {
                    AnimatedContent(
                        targetState = tagline,
                        transitionSpec = {
                            (fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 4 }) togetherWith fadeOut(tween(200))
                        },
                        label = "tagline",
                    ) { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                            color = c.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Box(Modifier.width(132.dp).height(72.dp)) {
                HorizonWidget(
                    date = date,
                    hour = hour,
                    sun = sun,
                    rise = play,
                    onTap = { infoShown = !infoShown },
                    onScrub = {
                        scrubHour = it
                        if (it != null) infoShown = false
                        onPreview(it?.let { h -> ((h % 24) + 24) % 24 })
                    },
                    description = description,
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = scrubHour != null,
                    enter = fadeIn(TgMotion.fast()) + scaleIn(TgMotion.fast(), initialScale = 0.9f),
                    exit = fadeOut(TgMotion.exit()) + scaleOut(TgMotion.exit(), targetScale = 0.9f),
                    modifier = Modifier.align(Alignment.TopEnd).offset(y = (-30).dp),
                ) {
                    Text(
                        "${TodayText.hhmm(shownHour)} · ${period.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.bg,
                        maxLines = 1,
                        modifier = Modifier
                            .background(c.text, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = infoShown && scrubHour == null,
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { -it / 2 },
            exit = fadeOut(tween(200)),
        ) {
            Text(
                info,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}
