package com.example.personal_studio.feature.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.CourseSeriesSummary
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.feature.timeline.today.pressScale
import com.example.personal_studio.feature.timeline.vm.CourseSeriesListViewModel
import com.example.personal_studio.ui.components.TgTopBar
import com.example.personal_studio.ui.components.TgEmptyState
import com.example.personal_studio.ui.components.TgGroupLabel
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.daylight.CoursePalette

/**
 * 课程管理 (from 设置): add a course by hand or import the term from the school, then every
 * course in its timetable colour with weekdays, periods, weeks, teacher and room.
 */
@Composable
fun CourseSeriesListScreen(
    onBack: () -> Unit,
    onOpenSeries: (Long) -> Unit,
    onAddCourse: () -> Unit,
    onImport: () -> Unit,
    vm: CourseSeriesListViewModel = hiltViewModel(),
) {
    val series by vm.series.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TgTopBar(route = "课程管理", subtitle = "课程管理\n${series.size} 门课程", onBack = onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        ) {
            item(key = "entries") {
                Row(Modifier.enterUp(true, 0), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Entry("添加课程", "手动录入一门课", Icons.Outlined.Add, Modifier.weight(1f), onAddCourse)
                    Entry("从教务导入", "导入本学期课表，先预览", Icons.Outlined.CloudDownload, Modifier.weight(1f), onImport)
                }
            }
            if (series.isEmpty()) {
                item(key = "empty") {
                    TgEmptyState(
                        "还没有课程",
                        "从教务导入整学期的课表，或者手动添加一门课；课程会出现在今日和周视图里，并在上课前提醒你。",
                        Modifier.padding(top = 28.dp).enterUp(true, 60),
                    )
                }
            } else {
                item(key = "label") { TgGroupLabel("全部课程", Modifier.enterUp(true, 60)) }
                items(series, key = { it.seriesId }) { s ->
                    CourseRow(s, onClick = { onOpenSeries(s.seriesId) }, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun Entry(title: String, hint: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = Tg.colors
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .heightIn(min = 112.dp)
            .pressScale(interaction)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(16.dp),
    ) {
        Box(Modifier.size(40.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.padding(top = 12.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Colour dot, name, "周一/周三 第1-2节 · 第1-16周 · 共32节 · 3学分", then teacher · room. */
@Composable
private fun CourseRow(s: CourseSeriesSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tg.colors
    val swatch = CoursePalette.forKey(s.title, c.family)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "编辑${s.title}", onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 6.dp).size(10.dp).background(swatch.content, CircleShape))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(s.title, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                formatCourseSchedule(s.weekdays, s.periodStart, s.periodEnd, s.minWeek, s.maxWeek, s.occurrenceCount, s.credits),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp),
            )
            val who = listOfNotNull(s.instructor?.takeIf { it.isNotBlank() }, s.location?.takeIf { it.isNotBlank() })
            if (who.isNotEmpty()) {
                Text(who.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 1.dp))
            }
        }
    }
}
